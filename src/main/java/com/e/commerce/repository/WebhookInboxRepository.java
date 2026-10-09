package com.e.commerce.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class WebhookInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean tryInsert(String eventId, UUID paymentId, String eventType, String payloadHash) {
        int insertedRows = jdbcTemplate.update("""
                INSERT INTO webhook_inbox (event_id, payment_id, event_type, payload_hash)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId, paymentId, eventType, payloadHash);
        return insertedRows == 1;
    }
}
