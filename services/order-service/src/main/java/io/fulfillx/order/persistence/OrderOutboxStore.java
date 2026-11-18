package io.fulfillx.order.persistence;

import io.fulfillx.order.messaging.OutboxRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public class OrderOutboxStore {
    private final JdbcTemplate jdbc;
    public OrderOutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean append(UUID messageId, String dedupeKey, UUID aggregateId, String messageType,
                          String destinationType, String destination, String payload) {
        return jdbc.update("INSERT INTO order_outbox(message_id, deduplication_key, aggregate_id, message_type, destination_type, destination, payload) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (deduplication_key) DO NOTHING",
                messageId, dedupeKey, aggregateId, messageType, destinationType, destination, payload) == 1;
    }

    @Transactional
    public List<OutboxRecord> claim(int limit, int leaseSeconds) {
        UUID token = UUID.randomUUID();
        String sql = "WITH candidates AS (SELECT message_id FROM order_outbox WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP " +
                "AND (lease_until IS NULL OR lease_until <= CURRENT_TIMESTAMP) ORDER BY created_at, message_id FOR UPDATE SKIP LOCKED LIMIT ?) " +
                "UPDATE order_outbox AS item SET lease_token = ?, lease_until = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), attempts = attempts + 1 " +
                "FROM candidates WHERE item.message_id = candidates.message_id " +
                "RETURNING item.message_id, item.deduplication_key, item.message_type, item.destination_type, item.destination, item.payload, item.attempts, item.lease_token";
        return jdbc.query(sql, (rs, row) -> new OutboxRecord(rs.getObject("message_id", UUID.class), rs.getString("deduplication_key"),
                rs.getString("message_type"), rs.getString("destination_type"), rs.getString("destination"), rs.getString("payload"),
                rs.getInt("attempts"), rs.getObject("lease_token", UUID.class)), limit, token, leaseSeconds);
    }

    @Transactional public void markPublished(UUID messageId, UUID leaseToken) {
        jdbc.update("UPDATE order_outbox SET published_at = CURRENT_TIMESTAMP, lease_token = NULL, lease_until = NULL WHERE message_id = ? AND lease_token = ?", messageId, leaseToken);
    }
    @Transactional public void scheduleRetry(UUID messageId, UUID token, int attempts, int retrySeconds) {
        int ceiling = (int) Math.min(300L, (long) Math.max(1, retrySeconds) * (1L << Math.min(8, Math.max(0, attempts - 1))));
        int delay = java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1, ceiling / 2), Math.max(2, ceiling + 1));
        jdbc.update("UPDATE order_outbox SET next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), lease_token = NULL, lease_until = NULL WHERE message_id = ? AND lease_token = ?", delay, messageId, token);
    }
}
