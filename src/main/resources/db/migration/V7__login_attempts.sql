CREATE TABLE login_attempt (
    key_hash varchar(64) PRIMARY KEY,
    failures timestamptz[] NOT NULL DEFAULT '{}',
    blocked_until timestamptz,
    retained_until timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT login_attempt_max_failures CHECK (cardinality(failures) <= 10)
);
CREATE INDEX login_attempt_retention_idx ON login_attempt (retained_until);
