ALTER TABLE bank_transactions
    ADD COLUMN payment_method VARCHAR(32),
    ADD COLUMN transaction_type VARCHAR(128),
    ADD COLUMN counterparty_iban VARCHAR(34);

UPDATE bank_transactions SET payment_method = 'UNKNOWN' WHERE payment_method IS NULL;

ALTER TABLE bank_transactions
    ALTER COLUMN payment_method SET NOT NULL,
    ALTER COLUMN payment_method SET DEFAULT 'UNKNOWN';

ALTER TABLE subscriptions
    ADD COLUMN sample_description VARCHAR(512),
    ADD COLUMN source_iban VARCHAR(34),
    ADD COLUMN payment_method VARCHAR(32);
