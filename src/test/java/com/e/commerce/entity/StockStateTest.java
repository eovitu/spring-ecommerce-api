package com.e.commerce.entity;

import com.e.commerce.enums.StockReservationStatus;
import com.e.commerce.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockStateTest {

    @Test
    void reservesOnlyTheAvailableQuantity() {
        Stock stock = Stock.criar(new Product(), 5);

        stock.reservar(3);

        assertEquals(5, stock.getTotalQuantity());
        assertEquals(3, stock.getReservedQuantity());
        assertEquals(2, stock.getAvailableQuantity());
        assertThrows(InsufficientStockException.class, () -> stock.reservar(3));
    }

    @Test
    void releasesAReservationWithoutReducingTotalStock() {
        Stock stock = Stock.criar(new Product(), 5);
        stock.reservar(3);

        stock.liberar(3);

        assertEquals(5, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    @Test
    void confirmsDeductionFromReservedAndTotalStock() {
        Stock stock = Stock.criar(new Product(), 5);
        stock.reservar(3);

        stock.confirmarBaixa(3);

        assertEquals(2, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    @Test
    void reservationExpiresExactlyFifteenMinutesAfterCreation() {
        Instant createdAt = Instant.parse("2026-09-10T12:00:00Z");
        Stock stock = Stock.criar(new Product(), 5);
        Order order = Order.criar(new User());

        StockReservation reservation = StockReservation.criar(stock, order, 2, createdAt);

        assertEquals(Instant.parse("2026-09-10T12:15:00Z"), reservation.getExpiresAt());
        assertEquals(StockReservationStatus.ATIVA, reservation.getStatus());
    }

    @Test
    void consumedReservationCannotExpireOrBeReleased() {
        Instant createdAt = Instant.parse("2026-09-10T12:00:00Z");
        StockReservation reservation = StockReservation.criar(
                Stock.criar(new Product(), 5), Order.criar(new User()), 2, createdAt
        );

        reservation.consumir();

        assertEquals(StockReservationStatus.CONSUMIDA, reservation.getStatus());
        assertThrows(IllegalStateException.class, () -> reservation.expirar(createdAt.plusSeconds(901)));
        assertThrows(IllegalStateException.class, reservation::liberar);
    }

    @Test
    void activeReservationCannotExpireBeforeItsDeadline() {
        Instant createdAt = Instant.parse("2026-09-10T12:00:00Z");
        StockReservation reservation = StockReservation.criar(
                Stock.criar(new Product(), 5), Order.criar(new User()), 2, createdAt
        );

        assertThrows(IllegalStateException.class, () -> reservation.expirar(createdAt.plusSeconds(899)));
        assertEquals(StockReservationStatus.ATIVA, reservation.getStatus());
    }
}
