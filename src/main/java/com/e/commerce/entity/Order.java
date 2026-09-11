package com.e.commerce.entity;

import com.e.commerce.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tb_orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime moment;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    @OneToOne(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JsonManagedReference
    private Payment payment;

    @OneToMany(mappedBy = "order", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JsonManagedReference
    private List<OrderItem> orderItems = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public static Order criar(User user) {
        Order order = new Order();
        order.user = Objects.requireNonNull(user, "Usuario e obrigatorio");
        order.status = OrderStatus.CRIADO;
        return order;
    }

    public void adicionarItem(Product product, int quantity) {
        Objects.requireNonNull(product, "Produto e obrigatorio");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }

        BigDecimal currentPrice = Objects.requireNonNull(product.getPrice(), "Preco do produto e obrigatorio");
        if (currentPrice.signum() < 0) {
            throw new IllegalArgumentException("Preco do produto nao pode ser negativo");
        }

        OrderItem item = new OrderItem();
        item.setOrder(this);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setPrice(currentPrice);
        orderItems.add(item);
    }

    public Payment criarIntencaoPagamento(LocalDate paymentDate) {
        exigirStatus(OrderStatus.CRIADO, "criar intencao de pagamento");
        if (orderItems.isEmpty()) {
            throw new IllegalStateException("Pedido deve possuir ao menos um item");
        }
        if (payment != null) {
            throw new IllegalStateException("Pedido ja possui uma intencao de pagamento");
        }

        payment = Payment.criarPendente(this, paymentDate);
        status = OrderStatus.AGUARDANDO_PAGAMENTO;
        return payment;
    }

    public void confirmarPagamento() {
        exigirStatus(OrderStatus.AGUARDANDO_PAGAMENTO, "confirmar pagamento");
        if (payment == null) {
            throw new IllegalStateException("Pedido nao possui pagamento");
        }

        payment.confirmar();
        status = OrderStatus.PAGO;
    }

    public void cancelar() {
        if (status != OrderStatus.CRIADO && status != OrderStatus.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException("Pedido no status " + status + " nao pode ser cancelado");
        }
        if (payment != null) {
            payment.cancelar();
        }
        status = OrderStatus.CANCELADO;
    }

    public void marcarComoEnviado() {
        exigirStatus(OrderStatus.PAGO, "enviar pedido");
        status = OrderStatus.ENVIADO;
    }

    public void marcarComoEntregue() {
        exigirStatus(OrderStatus.ENVIADO, "entregar pedido");
        status = OrderStatus.ENTREGUE;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    private void exigirStatus(OrderStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("Nao e possivel " + operation + " com pedido no status " + status);
        }
    }
}
