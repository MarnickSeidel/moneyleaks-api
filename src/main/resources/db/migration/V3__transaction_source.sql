-- Track where each transaction came from. Existing rows predate Open Banking and are
-- all CSV uploads, so backfill them and keep CSV_UPLOAD as the default for safety.
ALTER TABLE bank_transactions
    ADD COLUMN source VARCHAR(32);

UPDATE bank_transactions SET source = 'CSV_UPLOAD' WHERE source IS NULL;

ALTER TABLE bank_transactions
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN source SET DEFAULT 'CSV_UPLOAD';

CREATE INDEX idx_bank_transactions_source ON bank_transactions(source);
