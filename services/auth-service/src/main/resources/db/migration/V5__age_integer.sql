-- V4 created age as SMALLINT; the entity's Integer field maps to Hibernate's default INTEGER,
-- and ddl-auto: validate rejects the mismatch outright.
ALTER TABLE users ALTER COLUMN age TYPE INTEGER;
