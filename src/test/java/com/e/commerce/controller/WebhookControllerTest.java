package com.e.commerce.controller;

import com.e.commerce.config.SecurityConfig;
import com.e.commerce.service.JwtService;
import com.e.commerce.service.PaymentWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = WebhookController.class, properties = "security.webhook.secret=test-webhook-secret")
@Import(SecurityConfig.class)
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentWebhookService paymentWebhookService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private com.e.commerce.repository.UserRepository userRepository;

    @Test
    void rejectsBlankConfiguredWebhookSecret() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebhookController(paymentWebhookService, " ")
        );
    }

    @Test
    void rejectsInvalidWebhookSecret() throws Exception {
        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Webhook-Secret", "invalid")
                        .contentType("application/json")
                        .content(requestBody("evt-1", UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidWebhookSecretAndProcessesEvent() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentWebhookService.confirmPayment("evt-1", paymentId)).thenReturn(true);

        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Webhook-Secret", "test-webhook-secret")
                        .contentType("application/json")
                        .content(requestBody("evt-1", paymentId)))
                .andExpect(status().isOk());

        verify(paymentWebhookService).confirmPayment("evt-1", paymentId);
    }

    @Test
    void repeatedEventStillReturnsSuccess() throws Exception {
        UUID paymentId = UUID.randomUUID();
        when(paymentWebhookService.confirmPayment("evt-1", paymentId)).thenReturn(false);

        mockMvc.perform(post("/webhooks/payments")
                        .header("X-Webhook-Secret", "test-webhook-secret")
                        .contentType("application/json")
                        .content(requestBody("evt-1", paymentId)))
                .andExpect(status().isOk());
    }

    private String requestBody(String eventId, UUID paymentId) {
        return "{\"eventId\":\"" + eventId + "\",\"paymentId\":\"" + paymentId + "\"}";
    }
}
