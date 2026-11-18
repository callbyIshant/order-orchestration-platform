package io.fulfillx.inventory.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class ProcessedMessageStore {
    private final JdbcTemplate jdbc;
    public ProcessedMessageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean insertIfAbsent(UUID messageId, String messageType) {
        return jdbc.update("INSERT INTO processed_messages(message_id, message_type) VALUES (?, ?) ON CONFLICT (message_id) DO NOTHING", messageId, messageType) == 1;
    }
}
