-- ledger-service schema: the single source of truth for money movement.
--
-- Every table carries user_id and every query filters on it. There is no users table here —
-- auth-service owns identity, and the gateway supplies the id as a verified header.
--
-- Money is NUMERIC(15,2) throughout. Amounts are always stored positive; `kind` carries the
-- direction, which keeps SUM() aggregates unambiguous.

CREATE TABLE accounts (
    id              UUID          PRIMARY KEY,
    user_id         UUID          NOT NULL,
    name            VARCHAR(60)   NOT NULL,
    type            VARCHAR(20)   NOT NULL,
    opening_balance NUMERIC(15,2) NOT NULL DEFAULT 0,
    archived        BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_accounts_type
        CHECK (type IN ('CASH', 'BANK', 'EWALLET', 'CREDIT_CARD'))
);

-- Case-insensitive, and scoped to live accounts so an archived "GCash" does not block a new one.
CREATE UNIQUE INDEX ux_accounts_user_name
    ON accounts (user_id, lower(name)) WHERE NOT archived;

CREATE TABLE categories (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL,
    name       VARCHAR(60) NOT NULL,
    kind       VARCHAR(10) NOT NULL,
    -- NEEDS/WANTS/SAVINGS is what makes the 70-20-10 split computable.
    bucket     VARCHAR(10),
    color      VARCHAR(7)  NOT NULL,
    -- Seeded categories: renameable, but not deletable, since reports assume they exist.
    is_system  BOOLEAN     NOT NULL DEFAULT FALSE,
    archived   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_categories_kind   CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_categories_bucket CHECK (bucket IS NULL OR bucket IN ('NEEDS', 'WANTS', 'SAVINGS')),
    -- 70-20-10 divides spending, so only expenses carry a bucket. Enforcing it here means the
    -- bucket report can never hit an income row with a bucket.
    CONSTRAINT ck_categories_bucket_expense_only CHECK (
        (kind = 'EXPENSE' AND bucket IS NOT NULL) OR (kind = 'INCOME' AND bucket IS NULL)
    )
);

CREATE UNIQUE INDEX ux_categories_user_name
    ON categories (user_id, lower(name)) WHERE NOT archived;

CREATE TABLE transactions (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL,
    account_id  UUID          NOT NULL REFERENCES accounts (id),
    category_id UUID          NOT NULL REFERENCES categories (id),
    -- Denormalised from the category so summary aggregates need no join.
    kind        VARCHAR(10)   NOT NULL,
    amount      NUMERIC(15,2) NOT NULL,
    txn_date    DATE          NOT NULL,
    note        VARCHAR(255),
    -- Records what created this row: a person, or a planning-service debt payment, goal
    -- contribution, or recurring bill. source_id points back at that record.
    source_type VARCHAR(20)   NOT NULL DEFAULT 'MANUAL',
    source_id   UUID,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_transactions_kind   CHECK (kind IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_transactions_source CHECK (
        source_type IN ('MANUAL', 'RECURRING_BILL', 'DEBT_PAYMENT', 'GOAL_CONTRIBUTION')
    )
);

-- Drives the transaction list, which is always "this user, newest first".
CREATE INDEX ix_transactions_user_date ON transactions (user_id, txn_date DESC);
-- Drives the by-category report and every budget progress lookup.
CREATE INDEX ix_transactions_user_category_date ON transactions (user_id, category_id, txn_date);
CREATE INDEX ix_transactions_user_account ON transactions (user_id, account_id);
-- Lets planning-service find the transaction it created for a payment or contribution.
CREATE INDEX ix_transactions_source ON transactions (source_type, source_id)
    WHERE source_id IS NOT NULL;

-- Records that a user's starter accounts and categories have been created. Without this,
-- deleting every category would silently re-seed the defaults on the next request.
CREATE TABLE user_bootstrap (
    user_id      UUID        PRIMARY KEY,
    bootstrapped_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
