package com.e.commerce.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record WebhookPaymentRequest(
        @NotBlank @Size(max = 128) String eventId,
        @NotNull UUID paymentId
) {
}
