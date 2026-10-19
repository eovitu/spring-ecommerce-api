package com.e.commerce.entity;

import com.e.commerce.enums.StockReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockReservation {

    private static final Duration RESERVATION_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Stock stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private StockReservationStatus status;

    public static StockReservation criar(Stock stock, Order order, int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantidade deve ser maior que zero");
        }

        StockReservation reservation = new StockReservation();
        reservation.stock = Objects.requireNonNull(stock, "Estoque e obrigatorio");
        reservation.order = Objects.requireNonNull(order, "Pedido e obrigatorio");
        reservation.quantity = quantity;
        reservation.expiresAt = Objects.requireNonNull(now, "Instante de criacao e obrigatorio")
                .plus(RESERVATION_DURATION);
        reservation.status = StockReservationStatus.ATIVA;
        return reservation;
    }

    public boolean isExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void consumir() {
        exigirAtiva("consumir");
        status = StockReservationStatus.CONSUMIDA;
    }

    public void expirar(Instant now) {
        exigirAtiva("expirar");
        if (!isExpiredAt(now)) {
            throw new IllegalStateException("Reserva ainda nao expirou");
        }
        status = StockReservationStatus.EXPIRADA;
    }

    public void liberar() {
        exigirAtiva("liberar");
        status = StockReservationStatus.LIBERADA;
    }

    private void exigirAtiva(String operation) {
        if (status != StockReservationStatus.ATIVA) {
            throw new IllegalStateException("Nao e possivel " + operation + " reserva no status " + status);
        }
    }
}
