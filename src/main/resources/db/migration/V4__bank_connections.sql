-- Open Banking transactions are not backed by an uploaded file, so a bank transaction
-- may now exist without a parent statement.
ALTER TABLE bank_transactions
    ALTER COLUMN statement_id DROP NOT NULL;

-- A bank_connection represents an authorised link to a user's bank via an Open Banking
-- provider (e.g. GoCardless Bank Account Data). It stores only the provider's opaque
-- identifiers, never account credentials.
CREATE TABLE bank_connections (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 VARCHAR(64),
    provider                VARCHAR(32) NOT NULL,
    external_connection_id  VARCHAR(128),
    institution_id          VARCHAR(128),
    status                  VARCHAR(32) NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at              TIMESTAMPTZ
);

CREATE INDEX idx_bank_connections_user_id ON bank_connections(user_id);
CREATE INDEX idx_bank_connections_external_id ON bank_connections(external_connection_id);
