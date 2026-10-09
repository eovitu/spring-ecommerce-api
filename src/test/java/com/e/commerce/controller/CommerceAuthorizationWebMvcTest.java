package com.e.commerce.controller;

import com.e.commerce.dto.response.OrderResponse;
import com.e.commerce.dto.response.PaymentResponse;
import com.e.commerce.dto.request.OrderRequest;
import com.e.commerce.config.SecurityConfig;
import com.e.commerce.enums.OrderStatus;
import com.e.commerce.enums.Role;
import com.e.commerce.repository.OrderRepository;
import com.e.commerce.security.AuthenticatedUser;
import com.e.commerce.security.AuthorizationService;
import com.e.commerce.service.OrderService;
import com.e.commerce.service.PaymentService;
import com.e.commerce.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({OrderController.class, PaymentController.class})
@Import({SecurityConfig.class, CommerceAuthorizationWebMvcTest.AuthorizationConfiguration.class})
class CommerceAuthorizationWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderController orderController;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private OrderRepository orderRepository;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerListsOnlyOwnOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderService.findByUserId(userId)).thenReturn(List.of(orderResponse(orderId)));

        mockMvc.perform(get("/orders").with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isOk());

        verify(orderService).findByUserId(userId);
    }

    @Test
    void adminListsAllOrders() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.findAll()).thenReturn(List.of(orderResponse(orderId)));

        mockMvc.perform(get("/orders").with(authentication(userAuthentication(UUID.randomUUID(), Role.ADMIN))))
                .andExpect(status().isOk());

        verify(orderService).findAll();
    }

    @Test
    void nonOwnerCannotReadOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(false);

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void methodSecurityRejectsNonOwnerBeforeCallingController() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(userAuthentication(userId, Role.USER));
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(false);

        assertThrows(AuthorizationDeniedException.class, () -> orderController.findById(orderId));
    }

    @Test
    void adminCanReadAnyOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.findById(orderId)).thenReturn(orderResponse(orderId));

                mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(userAuthentication(UUID.randomUUID(), Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanReadOwnOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(true);
        when(orderService.findById(orderId)).thenReturn(orderResponse(orderId));

        mockMvc.perform(get("/orders/{id}", orderId)
                        .with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isOk());
    }

    @Test
    void creatingOrderUsesAuthenticatedUserAndRequiresNoPayloadUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        mockMvc.perform(post("/orders")
                        .with(authentication(userAuthentication(userId, Role.USER)))
                        .contentType("application/json")
                        .content("""
                                {"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(productId)))
                .andExpect(status().isCreated());

        verify(orderService).create(any(OrderRequest.class), eq(userId));
    }

    @Test
    void customerListsOnlyOwnPayments() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(paymentService.findByUserId(userId))
                .thenReturn(List.of(new PaymentResponse(paymentId, LocalDate.now(), paymentId)));

        mockMvc.perform(get("/payments").with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isOk());

        verify(paymentService).findByUserId(userId);
    }

    @Test
    void nonOwnerCannotReadPaymentThroughItsOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(orderRepository.existsByIdAndUserId(paymentId, userId)).thenReturn(false);

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanReadPaymentThroughItsOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(orderRepository.existsByIdAndUserId(paymentId, userId)).thenReturn(true);
        when(paymentService.findById(paymentId))
                .thenReturn(new PaymentResponse(paymentId, LocalDate.now(), paymentId));

        mockMvc.perform(get("/payments/{id}", paymentId)
                        .with(authentication(userAuthentication(userId, Role.USER))))
                .andExpect(status().isOk());
    }

    @Test
    void nonOwnerCannotCreatePaymentIntentForAnotherUsersOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(false);

        mockMvc.perform(post("/payments")
                        .with(authentication(userAuthentication(userId, Role.USER)))
                        .contentType("application/json")
                        .content("{\"orderId\":\"" + orderId + "\"}"))
                .andExpect(status().isForbidden());
    }

    private UsernamePasswordAuthenticationToken userAuthentication(UUID userId, Role role) {
        AuthenticatedUser principal = new AuthenticatedUser(userId, "user@example.com", role);
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
    }

    private OrderResponse orderResponse(UUID orderId) {
        return new OrderResponse(orderId, null, OrderStatus.CRIADO, "Customer", BigDecimal.TEN, List.of());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuthorizationConfiguration {

        @Bean
        AuthorizationService authorizationService(OrderRepository orderRepository) {
            return new AuthorizationService(orderRepository);
        }
    }

}
