-- admin-service schema: feedback from users, and the audit trail of what an admin did about it.
--
-- Nothing here is financial data — that stays in ledger-service and planning-service, per the
-- ownership rule the rest of this system already follows. This service owns only the things an
-- administrator does that a regular user's data model has no place for.

CREATE TABLE feedback (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL,
    -- Denormalised from the submitting client at write time, the same way the JWT carries
    -- "informational only" email/name claims. The trustworthy part of this row is user_id, which
    -- comes from the gateway-verified header; these two exist so the feedback list is readable
    -- without a network call back to auth-service for every row.
    user_email  VARCHAR(320)  NOT NULL,
    user_name   VARCHAR(100)  NOT NULL,
    category    VARCHAR(20)   NOT NULL,
    subject     VARCHAR(150)  NOT NULL,
    message     TEXT          NOT NULL,
    status      VARCHAR(12)   NOT NULL DEFAULT 'NEW',
    admin_note  TEXT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT ck_feedback_category CHECK (category IN ('BUG', 'IDEA', 'OTHER')),
    CONSTRAINT ck_feedback_status   CHECK (status IN ('NEW', 'REVIEWING', 'RESOLVED'))
);

CREATE INDEX ix_feedback_status ON feedback (status, created_at DESC);
CREATE INDEX ix_feedback_user ON feedback (user_id, created_at DESC);

-- An admin acting on a user is a different kind of event than a user acting on their own data,
-- and it deserves its own trail rather than being folded into a generic "who changed what" log
-- that would need to exist in every service.
CREATE TABLE admin_audit (
    id            UUID         PRIMARY KEY,
    actor_user_id UUID         NOT NULL,
    action        VARCHAR(40)  NOT NULL,
    target_type   VARCHAR(20),
    target_id     UUID,
    detail        VARCHAR(500),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_admin_audit_created ON admin_audit (created_at DESC);
CREATE INDEX ix_admin_audit_actor ON admin_audit (actor_user_id, created_at DESC);
