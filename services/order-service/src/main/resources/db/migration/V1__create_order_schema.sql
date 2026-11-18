CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('PENDING', 'INVENTORY_RESERVED', 'PAYMENT_AUTHORIZED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'COMPENSATION_PENDING')),
    total_amount NUMERIC(14, 2) NOT NULL CHECK (total_amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'INR'),
    payment_token VARCHAR(32),
    payment_id UUID,
    reservation_id UUID,
    requires_refund BOOLEAN NOT NULL DEFAULT FALSE,
    refund_completed BOOLEAN NOT NULL DEFAULT FALSE,
    requires_release BOOLEAN NOT NULL DEFAULT FALSE,
    release_completed BOOLEAN NOT NULL DEFAULT FALSE,
    history_sequence INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_orders_customer_created ON orders(customer_id, created_at DESC, order_id);

CREATE TABLE order_items (
    order_item_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(14, 2) NOT NULL CHECK (line_total >= 0),
    UNIQUE(order_id, product_id)
);

CREATE TABLE order_history (
    history_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE CASCADE,
    sequence_number INTEGER NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    detail_code VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,
    UNIQUE(order_id, sequence_number)
);
CREATE INDEX ix_order_history_order_sequence ON order_history(order_id, sequence_number);

CREATE TABLE idempotency_records (
    idempotency_record_id UUID PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    order_id UUID NOT NULL REFERENCES orders(order_id),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_idempotency_customer_key UNIQUE(customer_id, idempotency_key)
);
CREATE INDEX ix_idempotency_expiry ON idempotency_records(expires_at);

CREATE TABLE processed_messages (
    message_id UUID PRIMARY KEY,
    message_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_outbox (
    message_id UUID PRIMARY KEY,
    deduplication_key VARCHAR(200) NOT NULL UNIQUE,
    aggregate_id UUID NOT NULL,
    message_type VARCHAR(100) NOT NULL,
    destination_type VARCHAR(8) NOT NULL CHECK (destination_type IN ('SQS', 'SNS')),
    destination VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    lease_until TIMESTAMPTZ
);
CREATE INDEX ix_order_outbox_pending ON order_outbox(next_attempt_at, created_at) WHERE published_at IS NULL;
