package com.e.commerce.controller;

import com.e.commerce.dto.request.WebhookPaymentRequest;
import com.e.commerce.exception.UnauthorizedException;
import com.e.commerce.service.PaymentWebhookService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/webhooks/payments")
public class WebhookController {

    private final PaymentWebhookService paymentWebhookService;
    private final byte[] webhookSecret;

    public WebhookController(
            PaymentWebhookService paymentWebhookService,
            @Value("${security.webhook.secret}") String webhookSecret
    ) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("security.webhook.secret must not be blank");
        }
        this.paymentWebhookService = paymentWebhookService;
        this.webhookSecret = webhookSecret.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping
    public ResponseEntity<Void> confirmPayment(
            @RequestHeader(name = "X-Webhook-Secret", required = false) String providedSecret,
            @Valid @RequestBody WebhookPaymentRequest request
    ) {
        if (!isValidSecret(providedSecret)) {
            throw new UnauthorizedException("Webhook secret invalido");
        }

        paymentWebhookService.confirmPayment(request.eventId(), request.paymentId());
        return ResponseEntity.ok().build();
    }

    private boolean isValidSecret(String providedSecret) {
        if (providedSecret == null) {
            return false;
        }
        return MessageDigest.isEqual(
                webhookSecret,
                providedSecret.getBytes(StandardCharsets.UTF_8)
        );
    }
}
