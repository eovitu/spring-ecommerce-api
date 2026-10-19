package com.e.commerce.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {
    private final JdbcTemplate jdbc;

    /** UPSERT locks even a concurrently deleted/recreated key until commit. */
    public void lock(String key) {
        jdbc.queryForObject("""
            INSERT INTO login_attempt(key_hash) VALUES (?)
            ON CONFLICT (key_hash) DO UPDATE SET key_hash=EXCLUDED.key_hash
            RETURNING key_hash
            """, String.class, key);
    }

    public Instant now() {
        return jdbc.queryForObject("SELECT clock_timestamp()", java.sql.Timestamp.class).toInstant();
    }

    public Instant blockedUntil(String key) {
        return jdbc.queryForObject("SELECT blocked_until FROM login_attempt WHERE key_hash=?",
            (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(), key);
    }

    public void prune(String key, Instant now) {
        jdbc.update("""
            UPDATE login_attempt SET failures=ARRAY(
                SELECT t FROM unnest(failures) t WHERE t > ?::timestamptz - interval '15 minutes'),
                blocked_until=NULL
            WHERE key_hash=?
            """, java.sql.Timestamp.from(now), key);
    }

    public Instant recordFailure(String key, Instant now) {
        return jdbc.queryForObject("""
            UPDATE login_attempt SET failures=array_append(failures, ?::timestamptz),
                blocked_until=CASE WHEN cardinality(failures)=9 THEN ?::timestamptz + interval '15 minutes' END,
                retained_until=?::timestamptz + interval '15 minutes'
            WHERE key_hash=? RETURNING blocked_until
            """, (rs, row) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant(),
            java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), key);
    }

    /** Locked rows are skipped; the conditional delete cannot erase a fresh failure. */
    public int cleanup() {
        return jdbc.update("""
            WITH expired AS (
                SELECT key_hash FROM login_attempt WHERE retained_until <= clock_timestamp()
                ORDER BY key_hash LIMIT 500 FOR UPDATE SKIP LOCKED
            ) DELETE FROM login_attempt a USING expired e
            WHERE a.key_hash=e.key_hash AND a.retained_until <= clock_timestamp()
            """);
    }
}
