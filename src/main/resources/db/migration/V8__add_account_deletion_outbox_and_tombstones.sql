-- Durable retry queue for provider-side account erasure and a non-reversible
-- pseudonymous tombstone preventing deleted Keycloak identities from being
-- silently recreated by the OAuth callback.
CREATE TABLE account_deletion_outbox (
    keycloak_id UUID PRIMARY KEY,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(80)
);

CREATE INDEX idx_account_deletion_outbox_due
    ON account_deletion_outbox (next_attempt_at, requested_at);

CREATE TABLE deleted_user_identities (
    identity_hash CHAR(64) PRIMARY KEY,
    deleted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
