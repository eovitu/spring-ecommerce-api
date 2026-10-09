CREATE TABLE webhook_inbox (
    event_id VARCHAR(128) PRIMARY KEY,
    payment_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_inbox_payment
        FOREIGN KEY (payment_id) REFERENCES payment (order_id)
);

CREATE INDEX idx_webhook_inbox_payment_id ON webhook_inbox (payment_id);
CREATE INDEX idx_webhook_inbox_processed_at ON webhook_inbox (processed_at);
