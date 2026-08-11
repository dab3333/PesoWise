-- planning-service schema, part 2: debt (utang) tracking, both directions.
--
-- Money that has actually moved lives in the ledger, not here. A payment row records the debt
-- side of the event and keeps ledger_txn_id, a pointer to the transaction it created. This table
-- holds the balance owed; the ledger holds the cash movement.

CREATE TABLE debts (
    id            UUID          PRIMARY KEY,
    user_id       UUID          NOT NULL,
    name          VARCHAR(80)   NOT NULL,
    -- Who owes whom. Both directions matter: informal lending between family and friends is
    -- most of what "utang" means in practice.
    direction     VARCHAR(12)   NOT NULL,
    -- The person or institution on the other side. Optional: "Pag-IBIG loan" needs no name.
    counterparty  VARCHAR(80),
    principal     NUMERIC(15,2) NOT NULL,
    -- Reduced by payments. Starts equal to principal.
    balance       NUMERIC(15,2) NOT NULL,
    -- Recorded and displayed only. The MVP does not accrue interest; doing that properly needs a
    -- compounding schedule, which is out of scope.
    interest_rate NUMERIC(6,3),
    due_date      DATE,
    status        VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    settled_at    TIMESTAMPTZ,
    CONSTRAINT ck_debts_direction CHECK (direction IN ('OWED_BY_ME', 'OWED_TO_ME')),
    CONSTRAINT ck_debts_status    CHECK (status IN ('ACTIVE', 'SETTLED')),
    CONSTRAINT ck_debts_principal CHECK (principal > 0),
    -- Payments are rejected above the outstanding balance, so this can never go negative.
    CONSTRAINT ck_debts_balance   CHECK (balance >= 0 AND balance <= principal),
    CONSTRAINT ck_debts_interest  CHECK (interest_rate IS NULL OR interest_rate >= 0)
);

CREATE INDEX ix_debts_user_status ON debts (user_id, status, due_date);

CREATE TABLE debt_payments (
    id             UUID          PRIMARY KEY,
    debt_id        UUID          NOT NULL REFERENCES debts (id) ON DELETE CASCADE,
    user_id        UUID          NOT NULL,
    amount         NUMERIC(15,2) NOT NULL,
    paid_on        DATE          NOT NULL,
    note           VARCHAR(255),
    -- The transaction this payment created in the ledger database. No foreign key is possible
    -- across services; a null means the ledger write did not happen, which should not occur but
    -- is left nullable rather than pretending the constraint can be enforced here.
    ledger_txn_id  UUID,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_debt_payments_amount CHECK (amount > 0)
);

CREATE INDEX ix_debt_payments_debt ON debt_payments (debt_id, paid_on DESC);
CREATE INDEX ix_debt_payments_user ON debt_payments (user_id);

-- Lets a reconciliation check find the payment behind a ledger transaction, and makes a
-- double-posted payment impossible to hide.
CREATE UNIQUE INDEX ux_debt_payments_ledger_txn
    ON debt_payments (ledger_txn_id) WHERE ledger_txn_id IS NOT NULL;
