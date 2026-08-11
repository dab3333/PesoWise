-- auth-service schema: user identities only.
-- No financial data lives here; the ledger and planning services own that.

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Emails are stored lowercased by the application; a plain unique index is therefore
-- sufficient and lets the lookup use the index directly.
CREATE UNIQUE INDEX ux_users_email ON users (email);
