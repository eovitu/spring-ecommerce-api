package com.e.commerce.service;

import com.e.commerce.entity.Order;
import com.e.commerce.entity.Payment;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.User;
import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.PaymentStatus;
import com.e.commerce.repository.PaymentRepository;
import com.e.commerce.repository.WebhookInboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookServiceTest {

    @Mock
    private WebhookInboxRepository webhookInboxRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentWebhookService paymentWebhookService;

    @Test
    void repeatedEventConfirmsPaymentOnlyOnce() {
        UUID paymentId = UUID.randomUUID();
        String eventId = "evt-payment-confirmed-1";
        Order order = orderAwaitingPayment();
        Payment payment = order.getPayment();
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(webhookInboxRepository.tryInsert(
                eq(eventId), eq(paymentId), eq("PAYMENT_CONFIRMED"), anyString()
        )).thenReturn(true, false);

        boolean firstResult = paymentWebhookService.confirmPayment(eventId, paymentId);
        boolean repeatedResult = paymentWebhookService.confirmPayment(eventId, paymentId);

        assertTrue(firstResult);
        assertFalse(repeatedResult);
        assertEquals(OrderStatus.PAGO, order.getStatus());
        assertEquals(PaymentStatus.CONFIRMADO, payment.getStatus());
        verify(webhookInboxRepository, times(2))
                .tryInsert(eq(eventId), eq(paymentId), eq("PAYMENT_CONFIRMED"), anyString());
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
