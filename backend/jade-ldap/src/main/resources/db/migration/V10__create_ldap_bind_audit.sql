CREATE TABLE IF NOT EXISTS ldap_bind_audit (
    id BIGSERIAL PRIMARY KEY,
    bind_dn VARCHAR(512) NOT NULL,
    client_address VARCHAR(128),
    success BOOLEAN NOT NULL,
    result_code INTEGER NOT NULL,
    detail VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ldap_bind_audit_created_at ON ldap_bind_audit (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ldap_bind_audit_bind_dn ON ldap_bind_audit (bind_dn, created_at DESC);
