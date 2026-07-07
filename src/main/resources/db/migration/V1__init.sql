CREATE TABLE statements (
    id              BIGSERIAL PRIMARY KEY,
    filename        VARCHAR(255) NOT NULL,
    content_hash    VARCHAR(64) NOT NULL UNIQUE,
    content         BYTEA NOT NULL,
    status          VARCHAR(32) NOT NULL,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE TABLE bank_transactions (
    id                  BIGSERIAL PRIMARY KEY,
    statement_id        BIGINT NOT NULL REFERENCES statements(id) ON DELETE CASCADE,
    transaction_date    DATE NOT NULL,
    description         VARCHAR(512) NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    merchant_normalized VARCHAR(255) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bank_transactions_statement_id ON bank_transactions(statement_id);
CREATE INDEX idx_bank_transactions_merchant ON bank_transactions(merchant_normalized);

CREATE TABLE subscriptions (
    id                  BIGSERIAL PRIMARY KEY,
    merchant_normalized VARCHAR(255) NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL DEFAULT 'EUR',
    interval_type       VARCHAR(16) NOT NULL,
    occurrence_count    INTEGER NOT NULL,
    first_seen          DATE NOT NULL,
    last_seen           DATE NOT NULL,
    confidence          NUMERIC(4, 3) NOT NULL,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_normalized, amount, interval_type)
);
