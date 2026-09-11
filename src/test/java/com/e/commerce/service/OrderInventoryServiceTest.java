package com.e.commerce.service;

import com.e.commerce.dto.request.OrderItemRequest;
import com.e.commerce.dto.request.OrderRequest;
import com.e.commerce.entity.Order;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.entity.User;
import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.PaymentStatus;
import com.e.commerce.enums.StockReservationStatus;
import com.e.commerce.exception.InsufficientStockException;
import com.e.commerce.repository.OrderRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import com.e.commerce.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderInventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockReservationRepository stockReservationRepository;

    @Test
    void createsAwaitingPaymentOrderAndReservationsAfterLockingProductsInOrder() {
        UUID userId = UUID.randomUUID();
        UUID firstProductId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondProductId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Stock firstStock = stock(firstProductId, 10);
        Stock secondStock = stock(secondProductId, 10);
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(stockRepository.findByProductIdForUpdate(firstProductId)).thenReturn(Optional.of(firstStock));
        when(stockRepository.findByProductIdForUpdate(secondProductId)).thenReturn(Optional.of(secondStock));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrderService service = service();
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(secondProductId, 1),
                new OrderItemRequest(firstProductId, 2),
                new OrderItemRequest(firstProductId, 1)
        ));

        service.create(request, userId);

        InOrder lockOrder = inOrder(stockRepository);
        lockOrder.verify(stockRepository).findByProductIdForUpdate(firstProductId);
        lockOrder.verify(stockRepository).findByProductIdForUpdate(secondProductId);
        assertEquals(3, firstStock.getReservedQuantity());
        assertEquals(1, secondStock.getReservedQuantity());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order order = orderCaptor.getValue();
        assertEquals(OrderStatus.AGUARDANDO_PAGAMENTO, order.getStatus());
        assertEquals(PaymentStatus.PENDENTE, order.getPayment().getStatus());
        assertEquals(2, order.getOrderItems().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockReservation>> reservationsCaptor = ArgumentCaptor.forClass(List.class);
        verify(stockReservationRepository).saveAll(reservationsCaptor.capture());
        assertEquals(2, reservationsCaptor.getValue().size());
        assertEquals(StockReservationStatus.ATIVA, reservationsCaptor.getValue().getFirst().getStatus());
        assertEquals(NOW.plusSeconds(900), reservationsCaptor.getValue().getFirst().getExpiresAt());
    }

    @Test
    void validatesEveryLockedStockBeforeCreatingAnyReservation() {
        UUID userId = UUID.randomUUID();
        UUID firstProductId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondProductId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Stock firstStock = stock(firstProductId, 10);
        Stock secondStock = stock(secondProductId, 1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(stockRepository.findByProductIdForUpdate(firstProductId)).thenReturn(Optional.of(firstStock));
        when(stockRepository.findByProductIdForUpdate(secondProductId)).thenReturn(Optional.of(secondStock));
        OrderService service = service();
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest(firstProductId, 2),
                new OrderItemRequest(secondProductId, 2)
        ));

        InsufficientStockException exception = assertThrows(
                InsufficientStockException.class,
                () -> service.create(request, userId)
        );

        assertEquals(0, firstStock.getReservedQuantity());
        assertEquals(0, secondStock.getReservedQuantity());
        assertTrue(exception.getMessage().contains(secondProductId.toString()));
        verify(orderRepository, never()).save(any());
        verify(stockReservationRepository, never()).saveAll(any());
    }

    private OrderService service() {
        return new OrderService(
                orderRepository,
                userRepository,
                stockRepository,
                stockReservationRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private Stock stock(UUID productId, int quantity) {
        Product product = new Product();
        product.setId(productId);
        product.setName("Product " + productId);
        product.setPrice(BigDecimal.TEN);
        return Stock.criar(product, quantity);
    }
}
