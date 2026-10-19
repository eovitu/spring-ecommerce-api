package com.e.commerce.service;

import com.e.commerce.entity.Order;
import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.entity.User;
import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.PaymentStatus;
import com.e.commerce.enums.StockReservationStatus;
import com.e.commerce.repository.OrderRepository;
import com.e.commerce.repository.OutboxEventRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Test
    void expirationReleasesStockCancelsOrderAndCreatesOutboxEvent() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = awaitingOrder();
        Stock stock = stock(productId, 1);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(stock, order, 1, NOW.minusSeconds(901));
        when(reservationRepository.findProductIdsByOrderId(orderId)).thenReturn(List.of(productId));
        when(stockRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));
        when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

        boolean expired = service().expireOrderReservations(orderId);

        assertTrue(expired);
        assertEquals(1, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
        assertEquals(StockReservationStatus.EXPIRADA, reservation.getStatus());
        assertEquals(OrderStatus.CANCELADO, order.getStatus());
        assertEquals(PaymentStatus.CANCELADO, order.getPayment().getStatus());
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        assertEquals("pedido.cancelado.expiracao", eventCaptor.getValue().getEventType());
        assertEquals(orderId, eventCaptor.getValue().getAggregateId());
        assertFalse(eventCaptor.getValue().isPublished());
    }

    @Test
    void consumedReservationFoundAfterLockPreventsExpiration() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Order order = awaitingOrder();
        Stock stock = stock(productId, 1);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(stock, order, 1, NOW.minusSeconds(901));
        stock.confirmarBaixa(1);
        reservation.consumir();
        order.confirmarPagamento();
        when(reservationRepository.findProductIdsByOrderId(orderId)).thenReturn(List.of(productId));
        when(stockRepository.findByProductIdForUpdate(productId)).thenReturn(Optional.of(stock));
        when(reservationRepository.findByOrderIdForUpdate(orderId)).thenReturn(List.of(reservation));

        boolean expired = service().expireOrderReservations(orderId);

        assertFalse(expired);
        assertEquals(OrderStatus.PAGO, order.getStatus());
        assertEquals(StockReservationStatus.CONSUMIDA, reservation.getStatus());
        verify(orderRepository, never()).findByIdForUpdate(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private StockReservationExpirationService service() {
        return new StockReservationExpirationService(
                reservationRepository,
                stockRepository,
                orderRepository,
                outboxEventRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Order awaitingOrder() {
        Product product = new Product();
        product.setPrice(BigDecimal.TEN);
        Order order = Order.criar(new User());
        order.adicionarItem(product, 1);
        order.criarIntencaoPagamento(LocalDate.now());
        return order;
    }

    private Stock stock(UUID productId, int quantity) {
        Product product = new Product();
        product.setId(productId);
        return Stock.criar(product, quantity);
    }
}
