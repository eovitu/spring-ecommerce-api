CREATE TABLE tb_user (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    phone VARCHAR(11),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT chk_user_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE INDEX idx_email ON tb_user (email);
CREATE INDEX idx_role ON tb_user (role);
CREATE INDEX idx_created_at ON tb_user (created_at);

CREATE TABLE category (
    id UUID PRIMARY KEY,
    name VARCHAR(255)
);

CREATE TABLE product (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    price NUMERIC(38, 2),
    image_url VARCHAR(255)
);

CREATE TABLE tb_product_category (
    product_id UUID NOT NULL,
    category_id UUID NOT NULL,
    PRIMARY KEY (product_id, category_id),
    CONSTRAINT fk_product_category_product
        FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_product_category_category
        FOREIGN KEY (category_id) REFERENCES category (id)
);

CREATE TABLE tb_orders (
    id UUID PRIMARY KEY,
    moment TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    user_id UUID NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT chk_order_status CHECK (
        status IN ('CRIADO', 'AGUARDANDO_PAGAMENTO', 'PAGO', 'ENVIADO', 'ENTREGUE', 'CANCELADO')
    ),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES tb_user (id)
);

CREATE INDEX idx_orders_user_id ON tb_orders (user_id);
CREATE INDEX idx_orders_status ON tb_orders (status);
CREATE INDEX idx_orders_created_at ON tb_orders (created_at);

CREATE TABLE order_item (
    id UUID PRIMARY KEY,
    quantity INTEGER NOT NULL,
    price NUMERIC(38, 2) NOT NULL,
    order_id UUID NOT NULL,
    product_id UUID NOT NULL,
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_item_price CHECK (price >= 0),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES tb_orders (id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX idx_order_item_order_id ON order_item (order_id);
CREATE INDEX idx_order_item_product_id ON order_item (product_id);

CREATE TABLE payment (
    order_id UUID PRIMARY KEY,
    moment DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT chk_payment_status CHECK (status IN ('PENDENTE', 'CONFIRMADO', 'CANCELADO')),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES tb_orders (id)
);
