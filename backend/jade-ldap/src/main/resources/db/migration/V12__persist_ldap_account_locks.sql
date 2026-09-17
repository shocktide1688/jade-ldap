CREATE TABLE IF NOT EXISTS ldap_account_lock (
    normalized_dn VARCHAR(1024) PRIMARY KEY,
    bind_dn VARCHAR(1024) NOT NULL,
    failed_attempts INTEGER NOT NULL,
    last_failed_at TIMESTAMPTZ NOT NULL,
    locked_until TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_ldap_account_lock_locked_until
    ON ldap_account_lock (locked_until);
