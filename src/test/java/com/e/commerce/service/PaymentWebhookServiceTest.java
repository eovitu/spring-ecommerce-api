package com.e.commerce.service;

import com.e.commerce.entity.Order;
import com.e.commerce.entity.Payment;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.entity.User;
import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.PaymentStatus;
import com.e.commerce.repository.PaymentRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import com.e.commerce.repository.WebhookInboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private WebhookInboxRepository webhookInboxRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Test
    void repeatedEventConfirmsPaymentOnlyOnce() {
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt-payment-confirmed-1";
        Order order = orderAwaitingPayment();
        Payment payment = order.getPayment();
        Stock stock = Stock.criar(order.getOrderItems().getFirst().getProduct(), 1);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(
                stock, order, 1, NOW.minusSeconds(60)
        );
        when(paymentRepository.existsById(paymentId)).thenReturn(true);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(webhookInboxRepository.tryInsert(
                eq(eventId), eq(paymentId), eq("PAYMENT_CONFIRMED"), anyString()
        )).thenReturn(true, false);
        when(stockReservationRepository.findProductIdsByOrderId(paymentId)).thenReturn(List.of(paymentId));
        when(stockRepository.findByProductIdForUpdate(paymentId)).thenReturn(Optional.of(stock));
        when(stockReservationRepository.findByOrderIdForUpdate(paymentId)).thenReturn(List.of(reservation));

        PaymentWebhookService paymentWebhookService = service();

        boolean firstResult = paymentWebhookService.confirmPayment(eventId, paymentId);
        boolean repeatedResult = paymentWebhookService.confirmPayment(eventId, paymentId);

        assertTrue(firstResult);
        assertFalse(repeatedResult);
        assertEquals(OrderStatus.PAGO, order.getStatus());
        assertEquals(PaymentStatus.CONFIRMADO, payment.getStatus());
        assertEquals(0, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
        verify(webhookInboxRepository, times(2))
                .tryInsert(eq(eventId), eq(paymentId), eq("PAYMENT_CONFIRMED"), anyString());
    }

    @Test
    void lateWebhookReleasesActiveExpiredReservationAndFlagsReconciliation() {
        UUID paymentId = UUID.randomUUID();
        Order order = orderAwaitingPayment();
        Payment payment = order.getPayment();
        Stock stock = Stock.criar(order.getOrderItems().getFirst().getProduct(), 1);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(stock, order, 1, NOW.minusSeconds(901));
        when(paymentRepository.existsById(paymentId)).thenReturn(true);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(webhookInboxRepository.tryInsert(eq("evt-late"), eq(paymentId), anyString(), anyString()))
                .thenReturn(true);
        when(stockReservationRepository.findProductIdsByOrderId(paymentId)).thenReturn(List.of(paymentId));
        when(stockRepository.findByProductIdForUpdate(paymentId)).thenReturn(Optional.of(stock));
        when(stockReservationRepository.findByOrderIdForUpdate(paymentId)).thenReturn(List.of(reservation));

        service().confirmPayment("evt-late", paymentId);

        assertEquals(OrderStatus.RECONCILIACAO_PENDENTE, order.getStatus());
        assertEquals(PaymentStatus.RECONCILIACAO_PENDENTE, payment.getStatus());
        assertEquals(1, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
        assertEquals(com.e.commerce.enums.StockReservationStatus.EXPIRADA, reservation.getStatus());
    }

    @Test
    void webhookAfterExpirationJobFlagsReconciliationWithoutTouchingStockAgain() {
        UUID paymentId = UUID.randomUUID();
        Order order = orderAwaitingPayment();
        Payment payment = order.getPayment();
        Stock stock = Stock.criar(order.getOrderItems().getFirst().getProduct(), 1);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(stock, order, 1, NOW.minusSeconds(901));
        stock.liberar(1);
        reservation.expirar(NOW);
        order.cancelar();
        when(paymentRepository.existsById(paymentId)).thenReturn(true);
        when(paymentRepository.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(webhookInboxRepository.tryInsert(eq("evt-after-expiration"), eq(paymentId), anyString(), anyString()))
                .thenReturn(true);
        when(stockReservationRepository.findProductIdsByOrderId(paymentId)).thenReturn(List.of(paymentId));
        when(stockRepository.findByProductIdForUpdate(paymentId)).thenReturn(Optional.of(stock));
        when(stockReservationRepository.findByOrderIdForUpdate(paymentId)).thenReturn(List.of(reservation));

        service().confirmPayment("evt-after-expiration", paymentId);

        assertEquals(OrderStatus.RECONCILIACAO_PENDENTE, order.getStatus());
        assertEquals(PaymentStatus.RECONCILIACAO_PENDENTE, payment.getStatus());
        assertEquals(1, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    @Test
    void unknownPaymentFailsBeforeWritingWebhookInbox() {
        UUID paymentId = UUID.randomUUID();
        when(paymentRepository.existsById(paymentId)).thenReturn(false);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.e.commerce.exception.ResourceNotFoundException.class,
                () -> service().confirmPayment("evt-unknown", paymentId)
        );

        verify(webhookInboxRepository, never()).tryInsert(anyString(), eq(paymentId), anyString(), anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = OrderStatus.class, names = {"PAGO", "ENVIADO", "ENTREGUE", "RECONCILIACAO_PENDENTE"})
    void newEventDoesNotReprocessSettledPayment(OrderStatus expected) {
        UUID id = UUID.randomUUID();
        Order order = orderAwaitingPayment();
        Stock stock = Stock.criar(order.getOrderItems().getFirst().getProduct(), 2);
        stock.reservar(1);
        StockReservation reservation = StockReservation.criar(stock, order, 1, NOW.minusSeconds(60));
        if (expected == OrderStatus.RECONCILIACAO_PENDENTE) {
            stock.liberar(1);
            reservation.expirar(NOW.plusSeconds(901));
            order.sinalizarReconciliacaoPagamento();
        } else {
            stock.confirmarBaixa(1);
            reservation.consumir();
            order.confirmarPagamento();
            if (expected == OrderStatus.ENVIADO || expected == OrderStatus.ENTREGUE) order.marcarComoEnviado();
            if (expected == OrderStatus.ENTREGUE) order.marcarComoEntregue();
        }
        when(paymentRepository.existsById(id)).thenReturn(true);
        when(paymentRepository.findByIdForUpdate(id)).thenReturn(Optional.of(order.getPayment()));
        when(webhookInboxRepository.tryInsert(anyString(), eq(id), anyString(), anyString())).thenReturn(true);
        when(stockReservationRepository.findProductIdsByOrderId(id)).thenReturn(List.of(id));
        when(stockRepository.findByProductIdForUpdate(id)).thenReturn(Optional.of(stock));
        when(stockReservationRepository.findByOrderIdForUpdate(id)).thenReturn(List.of(reservation));
        assertTrue(service().confirmPayment("new-event", id));
        assertEquals(expected, order.getStatus());
        assertEquals(expected == OrderStatus.RECONCILIACAO_PENDENTE ? 2 : 1, stock.getTotalQuantity());
        assertEquals(0, stock.getReservedQuantity());
    }

    private PaymentWebhookService service() {
        return new PaymentWebhookService(
                webhookInboxRepository,
                paymentRepository,
                stockRepository,
                stockReservationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Order orderAwaitingPayment() {
        Product product = new Product();
        product.setPrice(BigDecimal.TEN);
        Order order = Order.criar(new User());
        order.adicionarItem(product, 1);
        order.criarIntencaoPagamento(LocalDate.now());
        return order;
    }
}
