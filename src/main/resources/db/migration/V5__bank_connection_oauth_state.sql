-- OAuth state returned by the bank redirect, used to match a consent callback to the
-- connection that initiated it (Enable Banking echoes the state we sent in POST /auth).
ALTER TABLE bank_connections
    ADD COLUMN oauth_state VARCHAR(64);

CREATE UNIQUE INDEX idx_bank_connections_oauth_state
    ON bank_connections (oauth_state)
    WHERE oauth_state IS NOT NULL;
