-- Corrects the token_hash column type introduced in V2.
--
-- V2 declared these CHAR(64), reasoning that a SHA-256 hex digest is always exactly 64
-- characters. That is true, but Postgres reports CHAR as `bpchar`, and Hibernate maps a String
-- field to `varchar` — so `ddl-auto: validate` refused to start the service:
--
--   Schema-validation: wrong column type encountered in column [token_hash]
--   in table [email_verification_tokens]; found [bpchar], but expecting [varchar(64)]
--
-- VARCHAR(64) is the better choice regardless. Postgres stores both identically and the manual
-- is explicit that CHAR(n) has no performance advantage; its only distinct behaviour is
-- blank-padding, which a fixed-width digest never needs.
--
-- Corrected forward rather than by editing V2: that migration has already been applied, and
-- Flyway validates checksums on every startup. Both tables are new in this release and hold no
-- rows worth preserving either way.

ALTER TABLE email_verification_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
ALTER TABLE password_reset_tokens      ALTER COLUMN token_hash TYPE VARCHAR(64);
