-- ============================================================
-- V1: Initial schema for Order Service
-- ============================================================

CREATE TABLE IF NOT EXISTS carts (
    id              UUID        PRIMARY KEY,
    user_id         UUID        NOT NULL,
    restaurant_id   UUID,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS cart_items (
    id              UUID            PRIMARY KEY,
    cart_id         UUID            NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    menu_item_id    UUID            NOT NULL,
    menu_item_name  VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12, 2)  NOT NULL,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'TRY'
);

CREATE TABLE IF NOT EXISTS orders (
    id                      UUID            PRIMARY KEY,
    user_id                 UUID            NOT NULL,
    restaurant_id           UUID            NOT NULL,
    cart_id                 UUID,
    correlation_id          VARCHAR(255),
    status                  VARCHAR(50)     NOT NULL,
    order_type              VARCHAR(50)     NOT NULL,
    total_amount            NUMERIC(12, 2)  NOT NULL,
    currency                VARCHAR(10)     NOT NULL DEFAULT 'TRY',
    delivery_fee_amount     NUMERIC(12, 2),
    delivery_fee_currency   VARCHAR(10),
    delivery_street         VARCHAR(255),
    delivery_district       VARCHAR(255),
    delivery_city           VARCHAR(255),
    delivery_postal_code    VARCHAR(20),
    delivery_lat            DOUBLE PRECISION,
    delivery_lng            DOUBLE PRECISION,
    payment_id              UUID,
    payment_status          VARCHAR(50),
    payment_method          VARCHAR(50),
    payment_timeout_at      TIMESTAMP,
    restaurant_timeout_at   TIMESTAMP,
    cancel_reason           TEXT,
    cancellation_reason     VARCHAR(100),
    notes                   TEXT,
    estimated_delivery_time TIMESTAMP,
    idempotency_key         VARCHAR(255)    UNIQUE,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id        ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_restaurant_id  ON orders(restaurant_id);
CREATE INDEX IF NOT EXISTS idx_orders_status         ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_idempotency    ON orders(idempotency_key);

CREATE TABLE IF NOT EXISTS order_items (
    id              UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id    UUID            NOT NULL,
    menu_item_name  VARCHAR(255)    NOT NULL,
    quantity        INT             NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(12, 2)  NOT NULL,
    currency        VARCHAR(10)     NOT NULL DEFAULT 'TRY'
);

CREATE TABLE IF NOT EXISTS order_status_history (
    id          UUID        PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(50),
    to_status   VARCHAR(50) NOT NULL,
    changed_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    changed_by  VARCHAR(255),
    reason      TEXT
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order ON order_status_history(order_id);

CREATE TABLE IF NOT EXISTS outbox_events (
    id              UUID        PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255),
    event_type      VARCHAR(100) NOT NULL,
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT         NOT NULL,
    processed       BOOLEAN      NOT NULL DEFAULT FALSE,
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON outbox_events(processed, retry_count, created_at)
    WHERE processed = FALSE;
