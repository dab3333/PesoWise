-- Adds "Money Lent" as a default category — it catches the outflow when a debt is created with
-- "I already gave them this money" switched on (see DebtService.create's disbursement handling).
--
-- BootstrapService's seed list only ever runs once per user, guarded by the user_bootstrap
-- marker (see its own class comment), so adding a new entry there only reaches users who
-- register from this point on. Every already-bootstrapped user needs this backfilled directly.
--
-- Not a "re-add what the user deleted" situation, which BootstrapService deliberately never
-- does — this category never existed for these users to have deleted in the first place.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

INSERT INTO categories (id, user_id, name, kind, bucket, color, is_system, archived)
SELECT gen_random_uuid(), ub.user_id, 'Money Lent', 'EXPENSE', 'SAVINGS', '#2563eb', true, false
FROM user_bootstrap ub
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = ub.user_id AND lower(c.name) = 'money lent' AND NOT c.archived
);
