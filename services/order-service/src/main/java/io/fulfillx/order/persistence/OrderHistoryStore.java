package io.fulfillx.order.persistence;

import io.fulfillx.order.api.dto.OrderHistoryResponse;
import io.fulfillx.order.domain.Order;
import io.fulfillx.order.domain.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OrderHistoryStore {
    private final JdbcTemplate jdbc;
    public OrderHistoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void append(Order order, String eventType, OrderStatus from, OrderStatus to, String detail) {
        Integer sequence = jdbc.queryForObject("UPDATE orders SET history_sequence = history_sequence + 1 WHERE order_id = ? RETURNING history_sequence", Integer.class, order.getId());
        jdbc.update("INSERT INTO order_history(history_id, order_id, sequence_number, event_type, from_status, to_status, detail_code, occurred_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), order.getId(), sequence, eventType,
                from == null ? null : from.name(), to.name(), detail, Timestamp.from(Instant.now()));
    }

    public List<OrderHistoryResponse> findByOrderId(UUID orderId) {
        return jdbc.query("SELECT history_id, sequence_number, event_type, from_status, to_status, detail_code, occurred_at FROM order_history WHERE order_id = ? ORDER BY sequence_number",
                (rs, row) -> new OrderHistoryResponse(rs.getObject("history_id", UUID.class), rs.getInt("sequence_number"),
                        rs.getString("event_type"), parseStatus(rs.getString("from_status")), parseStatus(rs.getString("to_status")),
                        rs.getString("detail_code"), rs.getTimestamp("occurred_at").toInstant()), orderId);
    }
    private static OrderStatus parseStatus(String value) { return value == null ? null : OrderStatus.valueOf(value); }
}
