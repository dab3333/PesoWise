-- Debt interest: stored accrual, not a derived schedule. Interest never enters `balance` —
-- that column keeps meaning "outstanding principal", and accrued interest lives in its own
-- column. That is what lets ck_debts_balance simplify to "not negative" instead of being dropped
-- outright: it is still a real invariant, just no longer bounded by principal now that interest
-- exists.
--
-- Simplified from the original design by one axis: interest_method is SIMPLE or COMPOUND, full
-- stop, with no separate MONTHLY/ANNUAL compounding choice. The accrual job only ever runs
-- monthly, so a once-a-year compounding option would need its own partial-period bookkeeping for
-- a distinction nothing here was asking for.

ALTER TABLE debts ADD COLUMN start_date       DATE          NOT NULL DEFAULT CURRENT_DATE;
ALTER TABLE debts ADD COLUMN interest_method  VARCHAR(10);
ALTER TABLE debts ADD COLUMN accrued_interest NUMERIC(15,2) NOT NULL DEFAULT 0;
-- Lifetime interest actually paid, for reporting — distinct from accrued_interest, which is only
-- what is currently outstanding and shrinks as it's paid off.
ALTER TABLE debts ADD COLUMN interest_paid_total NUMERIC(15,2) NOT NULL DEFAULT 0;
-- Null until the first accrual. The month it names is the last one already accrued; the next
-- pass picks up the month after it.
ALTER TABLE debts ADD COLUMN last_accrued_on DATE;

-- Backfill before the job can ever run against existing rows.
UPDATE debts SET start_date = created_at::date;

ALTER TABLE debts DROP CONSTRAINT ck_debts_balance;
ALTER TABLE debts ADD CONSTRAINT ck_debts_balance CHECK (balance >= 0);
ALTER TABLE debts ADD CONSTRAINT ck_debts_interest_accrued CHECK (accrued_interest >= 0);
ALTER TABLE debts ADD CONSTRAINT ck_debts_interest_paid_total CHECK (interest_paid_total >= 0);
ALTER TABLE debts ADD CONSTRAINT ck_debts_interest_method
    CHECK (interest_method IS NULL OR interest_method IN ('SIMPLE', 'COMPOUND'));
-- Interest only accrues where there is a rate to accrue at.
ALTER TABLE debts ADD CONSTRAINT ck_debts_interest_method_needs_rate
    CHECK (interest_method IS NULL OR interest_rate IS NOT NULL);

ALTER TABLE debt_payments ADD COLUMN principal_part NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE debt_payments ADD COLUMN interest_part  NUMERIC(15,2) NOT NULL DEFAULT 0;
-- Every historical payment predates interest, so it was pure principal by definition.
UPDATE debt_payments SET principal_part = amount;

ALTER TABLE debt_payments ADD CONSTRAINT ck_debt_payments_principal_part CHECK (principal_part >= 0);
ALTER TABLE debt_payments ADD CONSTRAINT ck_debt_payments_interest_part CHECK (interest_part >= 0);

CREATE TABLE debt_interest_accruals (
    id                 UUID          PRIMARY KEY,
    debt_id            UUID          NOT NULL REFERENCES debts (id) ON DELETE CASCADE,
    user_id            UUID          NOT NULL,
    -- The first day of the month this accrual is for.
    period             DATE          NOT NULL,
    amount             NUMERIC(15,2) NOT NULL,
    -- Balance the accrual was calculated against — principal only for SIMPLE, principal plus
    -- unpaid interest for COMPOUND — kept for anyone auditing the number later.
    balance_at_accrual NUMERIC(15,2) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_debt_interest_accruals_amount CHECK (amount >= 0)
);

-- The idempotency guard, exactly mirroring ux_recurring_runs_occurrence: one accrual per debt
-- per month, full stop.
CREATE UNIQUE INDEX ux_debt_accrual_period ON debt_interest_accruals (debt_id, period);
CREATE INDEX ix_debt_interest_accruals_user ON debt_interest_accruals (user_id);
