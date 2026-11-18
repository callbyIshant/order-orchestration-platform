CREATE TABLE processed_messages (
    message_id UUID PRIMARY KEY,
    message_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY,
    source_message_id UUID NOT NULL UNIQUE REFERENCES processed_messages(message_id),
    order_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL CHECK (event_type IN ('OrderConfirmed','OrderRejected','OrderCancelled')),
    correlation_id UUID NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','SENDING','DELIVERED','FAILED')),
    delivery_token UUID,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMPTZ,
    UNIQUE(order_id,event_type)
);
CREATE TABLE delivery_attempts (
    attempt_id UUID PRIMARY KEY,
    notification_id UUID NOT NULL REFERENCES notifications(notification_id) ON DELETE CASCADE,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('STARTED','DELIVERED','FAILED')),
    error_code VARCHAR(100),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    UNIQUE(notification_id,attempt_number)
);
