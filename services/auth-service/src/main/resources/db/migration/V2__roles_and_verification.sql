-- auth-service schema, part 2: authorisation and proof-of-email.
--
-- V1 treated every authenticated user as equal and took the address on the registration form at
-- face value. Neither holds once the app is deployed publicly: an admin panel needs a role to
-- gate on, and a password reset is only as trustworthy as the mailbox it is sent to.

ALTER TABLE users ADD COLUMN role           VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN email_verified BOOLEAN     NOT NULL DEFAULT false;
-- Disabled accounts keep their rows. Deleting a user would orphan their ledger and planning
-- data, which live in databases this service cannot reach.
ALTER TABLE users ADD COLUMN disabled       BOOLEAN     NOT NULL DEFAULT false;

ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));

-- Everyone who registered before verification existed is grandfathered in. Without this the
-- first deploy of this release locks out every existing account, including the developer's.
UPDATE users SET email_verified = true;

-- Both token tables store a SHA-256 hash of the token, never the token itself. A leaked backup
-- of this database must not be a working set of account-takeover links; the raw value exists
-- only in the email that was sent.
--
-- Neither table gets a "one live token per user" constraint: asking for a second reset link
-- because the first mail was slow is normal, and both should work until one is used.

CREATE TABLE email_verification_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- Set when redeemed. A non-null value means the token is spent and must be refused, which
    -- is why rows are consumed rather than deleted.
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_email_verification_token ON email_verification_tokens (token_hash);
-- Supports the resend cooldown, which asks for the user's most recent token.
CREATE INDEX ix_email_verification_user ON email_verification_tokens (user_id, created_at DESC);

CREATE TABLE password_reset_tokens (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_password_reset_token ON password_reset_tokens (token_hash);
-- Redeeming one reset token invalidates the user's others, which needs this lookup.
CREATE INDEX ix_password_reset_user ON password_reset_tokens (user_id, created_at DESC);
