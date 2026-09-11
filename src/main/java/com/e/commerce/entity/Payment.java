package com.e.commerce.entity;

import com.e.commerce.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @Column(name = "order_id")
    private UUID id;

    @Column(nullable = false)
    private LocalDate moment;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "order_id", nullable = false)
    @JsonBackReference
    private Order order;

    static Payment criarPendente(Order order, LocalDate paymentDate) {
        Payment payment = new Payment();
        payment.order = Objects.requireNonNull(order, "Pedido e obrigatorio");
        payment.moment = Objects.requireNonNull(paymentDate, "Data do pagamento e obrigatoria");
        payment.status = PaymentStatus.PENDENTE;
        return payment;
    }

    void confirmar() {
        exigirStatus(PaymentStatus.PENDENTE, "confirmar pagamento");
        status = PaymentStatus.CONFIRMADO;
    }

    void cancelar() {
        exigirStatus(PaymentStatus.PENDENTE, "cancelar pagamento");
        status = PaymentStatus.CANCELADO;
    }

    void sinalizarReconciliacao() {
        if (status != PaymentStatus.PENDENTE && status != PaymentStatus.CANCELADO) {
            throw new IllegalStateException("Pagamento no status " + status + " nao pode ser reconciliado");
        }
        status = PaymentStatus.RECONCILIACAO_PENDENTE;
    }

    private void exigirStatus(PaymentStatus expected, String operation) {
        if (status != expected) {
            throw new IllegalStateException("Nao e possivel " + operation + " com pagamento no status " + status);
        }
    }
}
