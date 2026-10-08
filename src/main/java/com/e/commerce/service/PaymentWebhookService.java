package com.e.commerce.service;

import com.e.commerce.entity.Payment;
import com.e.commerce.entity.StockReservation;
import com.e.commerce.enums.PaymentStatus;
import com.e.commerce.enums.StockReservationStatus;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.PaymentRepository;
import com.e.commerce.repository.StockRepository;
import com.e.commerce.repository.StockReservationRepository;
import com.e.commerce.repository.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentWebhookService {

    private static final String EVENT_TYPE = "PAYMENT_CONFIRMED";

    private final WebhookInboxRepository webhookInboxRepository;
    private final PaymentRepository paymentRepository;
    private final StockRepository stockRepository;
    private final StockReservationRepository stockReservationRepository;
    private final Clock clock;

    @Transactional
    public boolean confirmPayment(String eventId, UUID paymentId) {
        if (!paymentRepository.existsById(paymentId)) {
            throw new ResourceNotFoundException("Pagamento nao encontrado");
        }

        boolean inserted = webhookInboxRepository.tryInsert(
                eventId,
                paymentId,
                EVENT_TYPE,
                payloadHash(eventId, paymentId)
        );
        if (!inserted) {
            return false;
        }

        UUID orderId = paymentId;
        List<UUID> productIds = stockReservationRepository.findProductIdsByOrderId(orderId);
        // Webhook e expiracao adquirem os mesmos locks na mesma ordem; quem obtiver primeiro decide o estado.
        for (UUID productId : productIds.stream().sorted().toList()) {
            stockRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Estoque nao encontrado para o produto " + productId
                    ));
        }

        List<StockReservation> reservations = stockReservationRepository.findByOrderIdForUpdate(orderId);
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado"));
        if (payment.getStatus() == PaymentStatus.CONFIRMADO
                || payment.getStatus() == PaymentStatus.RECONCILIACAO_PENDENTE) {
            return true;
        }
        if (reservations.isEmpty()) {
            sinalizarReconciliacao(payment, eventId, "pedido sem reserva de estoque");
            return true;
        }

        Instant now = clock.instant();
        boolean hasExpiredOrReleasedReservation = reservations.stream()
                .anyMatch(reservation -> reservation.getStatus() != StockReservationStatus.ATIVA
                        || reservation.isExpiredAt(now));
        if (hasExpiredOrReleasedReservation) {
            reservations.stream()
                    .filter(reservation -> reservation.getStatus() == StockReservationStatus.ATIVA)
                    .filter(reservation -> reservation.isExpiredAt(now))
                    .forEach(reservation -> {
                        reservation.getStock().liberar(reservation.getQuantity());
                        reservation.expirar(now);
                    });
            sinalizarReconciliacao(payment, eventId, "reserva expirada ou liberada");
            return true;
        }

        for (StockReservation reservation : reservations) {
            reservation.getStock().confirmarBaixa(reservation.getQuantity());
            reservation.consumir();
        }
        payment.getOrder().confirmarPagamento();
        return true;
    }

    private void sinalizarReconciliacao(Payment payment, String eventId, String reason) {
        payment.getOrder().sinalizarReconciliacaoPagamento();
        log.warn(
                "payment_reconciliation_required eventId={} orderId={} reason={}",
                eventId,
                payment.getOrder().getId(),
                reason
        );
    }

    private String payloadHash(String eventId, UUID paymentId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] payload = (eventId + ":" + paymentId).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(payload));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao esta disponivel", e);
        }
    }
}
