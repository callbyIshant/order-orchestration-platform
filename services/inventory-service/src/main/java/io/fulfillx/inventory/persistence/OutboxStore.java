package io.fulfillx.inventory.persistence;

import io.fulfillx.inventory.messaging.OutboxMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxStore {
    private final JdbcTemplate jdbc;
    public OutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(UUID messageId, UUID aggregateId, String type, String payload) {
        jdbc.update("INSERT INTO inventory_outbox(message_id, aggregate_id, message_type, payload) VALUES (?, ?, ?, ?)", messageId, aggregateId, type, payload);
    }

    @Transactional
    public List<OutboxMessage> claim(int limit, int leaseSeconds) {
        UUID token = UUID.randomUUID();
        String sql = "WITH candidates AS (" +
                " SELECT message_id FROM inventory_outbox" +
                " WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP" +
                " AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP)" +
                " ORDER BY created_at, message_id FOR UPDATE SKIP LOCKED LIMIT ?" +
                ") UPDATE inventory_outbox AS outbox SET lease_token = ?," +
                " lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), attempts = attempts + 1" +
                " FROM candidates WHERE outbox.message_id = candidates.message_id" +
                " RETURNING outbox.message_id, outbox.aggregate_id, outbox.message_type, outbox.payload, outbox.attempts, outbox.lease_token";
        return jdbc.query(sql, (rs, row) -> new OutboxMessage(
                rs.getObject("message_id", UUID.class), rs.getObject("aggregate_id", UUID.class),
                rs.getString("message_type"), rs.getString("payload"), rs.getInt("attempts"), rs.getObject("lease_token", UUID.class)
        ), limit, token, leaseSeconds);
    }

    @Transactional
    public void markPublished(UUID messageId, UUID leaseToken) {
        jdbc.update("UPDATE inventory_outbox SET published_at = CURRENT_TIMESTAMP, lease_token = NULL, lease_until = NULL WHERE message_id = ? AND lease_token = ?", messageId, leaseToken);
    }

    @Transactional
    public void scheduleRetry(UUID messageId, UUID leaseToken, int attempts, int retrySeconds) {
        int ceiling = (int) Math.min(300L, (long) Math.max(retrySeconds, 1) * (1L << Math.min(8, Math.max(0, attempts - 1))));
        int delay = java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, ceiling / 2), Math.max(2, ceiling + 1));
        jdbc.update("UPDATE inventory_outbox SET next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), lease_token = NULL, lease_until = NULL WHERE message_id = ? AND lease_token = ?", delay, messageId, leaseToken);
    }
}
