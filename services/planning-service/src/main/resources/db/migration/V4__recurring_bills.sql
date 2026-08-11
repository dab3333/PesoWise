-- planning-service schema, part 4: recurring bills.
--
-- A bill is a TEMPLATE plus a cursor (next_run_date). The scheduler walks the cursor forward,
-- recording each occurrence it satisfies in recurring_runs.

CREATE TABLE recurring_bills (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    -- Both reference rows in the ledger database; no cross-service foreign key is possible.
    category_id   UUID          NOT NULL,
    account_id    UUID          NOT NULL,
    amount        NUMERIC(15,2) NOT NULL,
    frequency     VARCHAR(10)   NOT NULL,
    -- For MONTHLY, the day of the month the bill falls on. Kept separate from next_run_date so a
    -- bill due on the 31st stays on the 31st after passing through February, instead of being
    -- permanently dragged back to the 28th.
    day_of_period SMALLINT,
    -- The cursor: the next occurrence not yet satisfied.
    next_run_date DATE          NOT NULL,
    -- true: the scheduler records it automatically. false: it is flagged as due and waits for the
    -- user to confirm, which is the right default for amounts that vary.
    auto_post     BOOLEAN       NOT NULL DEFAULT FALSE,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_recurring_frequency CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT ck_recurring_amount    CHECK (amount > 0),
    CONSTRAINT ck_recurring_day       CHECK (day_of_period IS NULL OR day_of_period BETWEEN 1 AND 31)
);

-- Drives the scheduler's only query: "active bills whose next occurrence is due".
CREATE INDEX ix_recurring_due ON recurring_bills (next_run_date) WHERE active;
CREATE INDEX ix_recurring_user ON recurring_bills (user_id, active, next_run_date);

-- One row per occurrence actually satisfied.
--
-- THE UNIQUE INDEX BELOW IS THE POINT OF THIS TABLE. The scheduler runs on a timer, and a container
-- restart re-triggers it; without a record of which occurrences are already done, a bill would be
-- charged again on every restart. Claiming the row before posting turns a double-charge into a
-- rejected insert.
CREATE TABLE recurring_runs (
    id            UUID        PRIMARY KEY,
    bill_id       UUID        NOT NULL REFERENCES recurring_bills (id) ON DELETE CASCADE,
    user_id       UUID        NOT NULL,
    -- The occurrence this row satisfies, identified by its scheduled date.
    due_date      DATE        NOT NULL,
    -- Null when the occurrence was skipped rather than posted.
    ledger_txn_id UUID,
    skipped       BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_recurring_runs_occurrence ON recurring_runs (bill_id, due_date);
CREATE INDEX ix_recurring_runs_user ON recurring_runs (user_id, created_at DESC);
