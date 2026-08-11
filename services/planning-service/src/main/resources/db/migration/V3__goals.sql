-- planning-service schema, part 3: savings goals.
--
-- Unlike debts, a goal stores NO running total. The amount saved is SUM(contributions), computed on
-- read.
--
-- The difference is deliberate rather than inconsistent. A debt has an invariant to protect -- you
-- cannot pay more than you owe -- so its balance is a guarded column with a CHECK enforcing it. A
-- goal has no such bound: saving more than you targeted is a good outcome, not an error. With
-- nothing to guard, a stored total would be pure duplication and one more thing that can drift.

CREATE TABLE goals (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    target_amount NUMERIC(15,2) NOT NULL,
    target_date   DATE,
    -- User-controlled: hides a goal without deleting its contribution history. "Achieved" is not a
    -- column -- it is simply saved >= target, computed on read.
    archived      BOOLEAN       NOT NULL DEFAULT FALSE,
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_goals_target CHECK (target_amount > 0)
);

CREATE INDEX ix_goals_user ON goals (user_id, archived, target_date);

CREATE TABLE goal_contributions (
    id             UUID          PRIMARY KEY,
    goal_id        UUID          NOT NULL REFERENCES goals (id) ON DELETE CASCADE,
    user_id        UUID          NOT NULL,
    amount         NUMERIC(15,2) NOT NULL,
    contributed_on DATE          NOT NULL,
    note           VARCHAR(255),
    -- The transaction this contribution created in the ledger database. No cross-service foreign
    -- key is possible.
    ledger_txn_id  UUID,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_goal_contributions_amount CHECK (amount > 0)
);

CREATE INDEX ix_goal_contributions_goal ON goal_contributions (goal_id, contributed_on DESC);
CREATE INDEX ix_goal_contributions_user ON goal_contributions (user_id);

-- Makes a double-posted contribution impossible to hide, and lets a reconciliation check find the
-- contribution behind a ledger transaction.
CREATE UNIQUE INDEX ux_goal_contributions_ledger_txn
    ON goal_contributions (ledger_txn_id) WHERE ledger_txn_id IS NOT NULL;
