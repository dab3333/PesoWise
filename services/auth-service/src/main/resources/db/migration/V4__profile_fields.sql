-- Collected at registration for the account-personalization features that will read them
-- later; nothing today depends on any of this. All nullable and never backfilled — accounts
-- created before this migration simply have none of it, which is fine since nothing reads it
-- yet.
ALTER TABLE users ADD COLUMN first_name VARCHAR(60);
ALTER TABLE users ADD COLUMN last_name  VARCHAR(60);
ALTER TABLE users ADD COLUMN age        SMALLINT;
ALTER TABLE users ADD COLUMN gender     VARCHAR(20);
ALTER TABLE users ADD COLUMN occupation VARCHAR(30);
ALTER TABLE users ADD COLUMN occupation_other VARCHAR(100);

ALTER TABLE users ADD CONSTRAINT ck_users_gender
    CHECK (gender IN ('MALE', 'FEMALE', 'UNSPECIFIED'));

ALTER TABLE users ADD CONSTRAINT ck_users_occupation
    CHECK (occupation IN (
        'STUDENT', 'EMPLOYED_PRIVATE', 'EMPLOYED_GOVERNMENT', 'SELF_EMPLOYED',
        'BUSINESS_OWNER', 'OFW', 'UNEMPLOYED', 'RETIRED', 'OTHER'
    ));

ALTER TABLE users ADD CONSTRAINT ck_users_age
    CHECK (age IS NULL OR (age >= 1 AND age <= 120));
