package com.e.commerce.service;

import com.e.commerce.entity.Payment;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.PaymentRepository;
import com.e.commerce.repository.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final String EVENT_TYPE = "PAYMENT_CONFIRMED";

    private final WebhookInboxRepository webhookInboxRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public boolean confirmPayment(String eventId, UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento nao encontrado"));

        boolean inserted = webhookInboxRepository.tryInsert(
                eventId,
                paymentId,
                EVENT_TYPE,
                payloadHash(eventId, paymentId)
        );
        if (!inserted) {
            return false;
        }

        payment.getOrder().confirmarPagamento();
        return true;
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
