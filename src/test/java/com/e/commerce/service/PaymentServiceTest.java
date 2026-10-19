package com.e.commerce.service;
import com.e.commerce.dto.request.PaymentRequest;
import com.e.commerce.entity.*;
import com.e.commerce.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PaymentServiceTest {
 @Test void existingPaymentIsReturnedUnderOrderLock() {
  var orders = mock(OrderRepository.class); var payments = mock(PaymentRepository.class);
  UUID id = UUID.randomUUID(); Product product = new Product(); product.setPrice(BigDecimal.TEN);
  Order order = Order.criar(new User()); order.adicionarItem(product, 1); order.criarIntencaoPagamento(LocalDate.now());
  when(orders.findByIdForUpdate(id)).thenReturn(Optional.of(order));
  var response = new PaymentService(payments, orders).create(new PaymentRequest(id));
  assertEquals(order.getPayment().getMoment(), response.getMoment());
  verify(orders).findByIdForUpdate(id); verifyNoInteractions(payments);
 }
}
