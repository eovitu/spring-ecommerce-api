package com.e.commerce.service;

import com.e.commerce.entity.Order;
import com.e.commerce.entity.OutboxEvent;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.enums.StockReservationStatus;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.OrderRepository;
import com.e.commerce.repository.OutboxEventRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReservationExpirationService {

    private final StockReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final Clock clock;

    @Transactional
    public boolean expireOrderReservations(UUID orderId) {
        List<UUID> productIds = reservationRepository.findProductIdsByOrderId(orderId);
        // Webhook e expiracao bloqueiam os mesmos SKUs em ordem crescente para evitar deadlock.
        for (UUID productId : productIds.stream().sorted().toList()) {
            stockRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Estoque nao encontrado para o produto " + productId
                    ));
        }

        List<StockReservation> reservations = reservationRepository.findByOrderIdForUpdate(orderId);
        Instant now = clock.instant();
        if (reservations.isEmpty() || reservations.stream().anyMatch(
                reservation -> reservation.getStatus() != StockReservationStatus.ATIVA
                        || !reservation.isExpiredAt(now)
        )) {
            return false;
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido nao encontrado"));
        for (StockReservation reservation : reservations) {
            reservation.getStock().liberar(reservation.getQuantity());
            reservation.expirar(now);
        }
        order.cancelar();
        outboxEventRepository.save(OutboxEvent.pedidoCanceladoPorExpiracao(orderId, now));
        return true;
    }
}
