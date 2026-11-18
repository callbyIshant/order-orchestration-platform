CREATE TABLE inventory_items (
    product_id UUID PRIMARY KEY,
    on_hand_quantity BIGINT NOT NULL CHECK (on_hand_quantity >= 0),
    reserved_quantity BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_reserved_within_on_hand CHECK (reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity)
);

CREATE TABLE reservations (
    reservation_id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED', 'REJECTED')),
    rejection_code VARCHAR(64),
    rejection_product_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservation_lines (
    reservation_id UUID NOT NULL REFERENCES reservations(reservation_id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, product_id)
);
CREATE INDEX ix_reservation_lines_product_id ON reservation_lines(product_id);

CREATE TABLE processed_messages (
    message_id UUID PRIMARY KEY,
    message_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE inventory_outbox (
    message_id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    message_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    lease_until TIMESTAMPTZ
);
CREATE INDEX ix_inventory_outbox_pending ON inventory_outbox(next_attempt_at, created_at) WHERE published_at IS NULL;
