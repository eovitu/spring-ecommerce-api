ALTER TABLE tb_orders DROP CONSTRAINT chk_order_status;
ALTER TABLE tb_orders ADD CONSTRAINT chk_order_status CHECK (
    status IN (
        'CRIADO',
        'AGUARDANDO_PAGAMENTO',
        'PAGO',
        'ENVIADO',
        'ENTREGUE',
        'CANCELADO',
        'RECONCILIACAO_PENDENTE'
    )
);

ALTER TABLE payment DROP CONSTRAINT chk_payment_status;
ALTER TABLE payment ADD CONSTRAINT chk_payment_status CHECK (
    status IN ('PENDENTE', 'CONFIRMADO', 'CANCELADO', 'RECONCILIACAO_PENDENTE')
);

CREATE TABLE stock (
    product_id UUID PRIMARY KEY,
    total_quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT chk_stock_total_non_negative CHECK (total_quantity >= 0),
    CONSTRAINT chk_stock_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_stock_reserved_within_total CHECK (reserved_quantity <= total_quantity)
);

INSERT INTO stock (product_id, total_quantity, reserved_quantity)
SELECT id, 100, 0
FROM product;

CREATE TABLE stock_reservation (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    order_id UUID NOT NULL,
    quantity INTEGER NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_stock_reservation_stock FOREIGN KEY (product_id) REFERENCES stock (product_id),
    CONSTRAINT fk_stock_reservation_order FOREIGN KEY (order_id) REFERENCES tb_orders (id),
    CONSTRAINT chk_stock_reservation_quantity CHECK (quantity > 0),
    CONSTRAINT chk_stock_reservation_status CHECK (
        status IN ('ATIVA', 'CONSUMIDA', 'EXPIRADA', 'LIBERADA')
    ),
    CONSTRAINT uk_stock_reservation_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX idx_stock_reservation_active_expiration
    ON stock_reservation (expires_at, order_id)
    WHERE status = 'ATIVA';
CREATE INDEX idx_stock_reservation_order_id ON stock_reservation (order_id);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_event_pending
    ON outbox_event (occurred_at, id)
    WHERE published_at IS NULL;
