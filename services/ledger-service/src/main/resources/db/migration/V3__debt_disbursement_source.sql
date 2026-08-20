-- Recording a debt where "someone owes me" is the moment the cash actually leaves an account —
-- unlike "I owe this", where nothing has moved yet at creation time, only when a payment is made.
-- DEBT_DISBURSEMENT tags that outflow so it's auditable back to the debt, same as DEBT_PAYMENT
-- already does for the repayment leg.
ALTER TABLE transactions DROP CONSTRAINT ck_transactions_source;
ALTER TABLE transactions ADD CONSTRAINT ck_transactions_source CHECK (
    source_type IN ('MANUAL', 'RECURRING_BILL', 'DEBT_PAYMENT', 'GOAL_CONTRIBUTION', 'DEBT_DISBURSEMENT')
);
