package com.e.commerce.entity;

import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateTest {

    @Test
    void createsAValidOrderAndPaymentIntent() {
        Order order = Order.criar(new User());
        Product product = productWithPrice(new BigDecimal("49.90"));

        order.adicionarItem(product, 2);
        Payment payment = order.criarIntencaoPagamento(LocalDate.of(2026, 9, 10));

        assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, order.getStatus());
        assertEquals(PaymentStatus.PENDENTE, payment.getStatus());
        assertEquals(order, payment.getOrder());
        assertEquals(payment, order.getPayment());
        assertEquals(1, order.getOrderItems().size());
        assertEquals(new BigDecimal("49.90"), order.getOrderItems().getFirst().getPrice());
    }

    @Test
    void confirmsOnlyAnAwaitingPaymentOrder() {
        Order order = orderAwaitingPayment();

        order.confirmarPagamento();

        assertEquals(OrderStatus.PAGO, order.getStatus());
        assertEquals(PaymentStatus.CONFIRMADO, order.getPayment().getStatus());
    }

    @Test
    void rejectsPaymentConfirmationForCancelledOrder() {
        Order order = orderAwaitingPayment();
        order.cancelar();

        assertThrows(IllegalStateException.class, order::confirmarPagamento);
        assertEquals(PaymentStatus.CANCELADO, order.getPayment().getStatus());
    }

    @Test
    void flagsLatePaymentForManualReconciliation() {
        Order order = orderAwaitingPayment();
        order.cancelar();

        order.sinalizarReconciliacaoPagamento();

        assertEquals(OrderStatus.RECONCILIACAO_PENDENTE, order.getStatus());
        assertEquals(PaymentStatus.RECONCILIACAO_PENDENTE, order.getPayment().getStatus());
    }

    @Test
    void rejectsCancellationAfterPaymentConfirmation() {
        Order order = orderAwaitingPayment();
        order.confirmarPagamento();

        assertThrows(IllegalStateException.class, order::cancelar);
    }

    @Test
    void rejectsInvalidOrderItems() {
        Order order = Order.criar(new User());
        Product product = productWithPrice(BigDecimal.TEN);

        assertThrows(IllegalArgumentException.class, () -> order.adicionarItem(product, 0));
        assertThrows(NullPointerException.class, () -> order.adicionarItem(null, 1));
    }

    private Order orderAwaitingPayment() {
        Order order = Order.criar(new User());
        order.adicionarItem(productWithPrice(BigDecimal.TEN), 1);
        Payment payment = order.criarIntencaoPagamento(LocalDate.now());
        assertNotNull(payment);
        return order;
    }

    private Product productWithPrice(BigDecimal price) {
        Product product = new Product();
        product.setPrice(price);
        return product;
    }
}
