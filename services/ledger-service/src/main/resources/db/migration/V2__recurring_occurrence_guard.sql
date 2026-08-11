-- Makes posting a recurring bill idempotent at the ledger, not just at the caller.
--
-- planning-service already claims each occurrence in its own recurring_runs table before posting,
-- which stops the ordinary duplicate: a container restart re-triggering the scheduler. But that
-- guard only holds once its transaction commits. If the ledger write succeeds and planning-service
-- then fails to commit, the claim rolls back and the next run would post the same bill a second
-- time -- charging the user twice.
--
-- This index closes that window. A given bill can produce at most one transaction per date, so a
-- retry is rejected by the database rather than silently duplicating money.
--
-- Deliberately scoped to RECURRING_BILL only. Two debt payments or two goal contributions on the
-- same day are perfectly legitimate, so the same constraint would be wrong for them.

CREATE UNIQUE INDEX ux_transactions_recurring_occurrence
    ON transactions (user_id, source_id, txn_date)
    WHERE source_type = 'RECURRING_BILL' AND source_id IS NOT NULL;
