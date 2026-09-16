CREATE TABLE IF NOT EXISTS ldap_directory_entry (
    normalized_dn VARCHAR(1024) PRIMARY KEY,
    dn VARCHAR(1024) NOT NULL,
    entry_ldif TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ldap_directory_revision (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    revision BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO ldap_directory_revision (id, revision)
VALUES (1, 0)
ON CONFLICT (id) DO NOTHING;
