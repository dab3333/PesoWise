-- planning-service schema, part 1: budgets.
--
-- Debts, goals and recurring bills arrive in later migrations, as their build steps land.
--
-- This service stores INTENT. It holds no transactions and no totals: how much has actually been
-- spent against a budget is read live from ledger-service, so there is no cached figure to drift.
--
-- category_id references a row in the ledger database. There is no foreign key — that is the
-- cost of database-per-service — so the application tolerates a category that has been archived.

CREATE TABLE budgets (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL,
    category_id  UUID          NOT NULL,
    -- Always the first of the month; the day carries no meaning.
    period_month DATE          NOT NULL,
    limit_amount NUMERIC(15,2) NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_budgets_limit_positive CHECK (limit_amount > 0)
);

-- One budget per category per month. This is what makes the upsert safe: two concurrent
-- "apply suggestion" requests cannot create duplicate limits for the same category.
CREATE UNIQUE INDEX ux_budgets_user_category_month
    ON budgets (user_id, category_id, period_month);

-- Drives the only read this table serves: "all budgets for this user in this month".
CREATE INDEX ix_budgets_user_month ON budgets (user_id, period_month);
