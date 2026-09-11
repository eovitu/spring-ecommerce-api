package com.e.commerce.service;

import com.e.commerce.dto.request.OrderItemRequest;
import com.e.commerce.dto.request.OrderRequest;
import com.e.commerce.dto.response.OrderItemResponse;
import com.e.commerce.dto.response.OrderResponse;
import com.e.commerce.entity.Order;
import com.e.commerce.entity.OrderItem;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.entity.User;
import com.e.commerce.exception.InsufficientStockException;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.OrderRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import com.e.commerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Gerencia o ciclo de vida de pedidos e a montagem da representação de saída.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final StockRepository stockRepository;
    private final StockReservationRepository stockReservationRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado"));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findByUserId(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("Usuario nao encontrado");
        }

        return orderRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse create(OrderRequest request, UUID authenticatedUserId) {
        User user = userRepository.findById(authenticatedUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));

        Map<UUID, Integer> quantitiesByProduct = new TreeMap<>();
        for (OrderItemRequest item : request.getItems()) {
            quantitiesByProduct.merge(item.getProductId(), item.getQuantity(), Math::addExact);
        }

        Map<UUID, Stock> lockedStocks = new LinkedHashMap<>();
        for (UUID productId : quantitiesByProduct.keySet()) {
            Stock stock = stockRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Estoque nao encontrado para o produto " + productId
                    ));
            lockedStocks.put(productId, stock);
        }

        List<UUID> insufficientProducts = quantitiesByProduct.entrySet().stream()
                .filter(entry -> lockedStocks.get(entry.getKey()).getAvailableQuantity() < entry.getValue())
                .map(Map.Entry::getKey)
                .toList();
        if (!insufficientProducts.isEmpty()) {
            throw new InsufficientStockException("Estoque insuficiente para os produtos " + insufficientProducts);
        }

        Order order = Order.criar(user);
        for (Map.Entry<UUID, Integer> entry : quantitiesByProduct.entrySet()) {
            Stock stock = lockedStocks.get(entry.getKey());
            stock.reservar(entry.getValue());
            order.adicionarItem(stock.getProduct(), entry.getValue());
        }
        order.criarIntencaoPagamento(LocalDate.now(clock));

        Order savedOrder = orderRepository.save(order);
        Instant now = clock.instant();
        List<StockReservation> reservations = quantitiesByProduct.entrySet().stream()
                .map(entry -> StockReservation.criar(
                        lockedStocks.get(entry.getKey()), savedOrder, entry.getValue(), now
                ))
                .toList();
        stockReservationRepository.saveAll(reservations);

        return toResponse(savedOrder);
    }

    /**
     * Converte o pedido e seus itens em DTO, incluindo o cálculo de subtotais e total.
     */
    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (order.getOrderItems() != null) {
            for (OrderItem item : order.getOrderItems()) {
                BigDecimal subtotal = item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                total = total.add(subtotal);

                items.add(new OrderItemResponse(
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getPrice(),
                        item.getQuantity(),
                        subtotal
                ));
            }
        }

        return new OrderResponse(
                order.getId(),
                order.getMoment(),
                order.getStatus(),
                order.getUser().getName(),
                total,
                items
        );
    }
}
