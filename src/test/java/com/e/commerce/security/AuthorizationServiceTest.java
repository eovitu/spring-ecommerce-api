package com.e.commerce.security;

import com.e.commerce.enums.Role;
import com.e.commerce.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AuthorizationService authorizationService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recognizesTheAuthenticatedUserAsTheResourceOwner() {
        UUID userId = UUID.randomUUID();
        authenticate(new AuthenticatedUser(userId, "customer@example.com", Role.USER));

        assertTrue(authorizationService.isOwner(userId));
    }

    @Test
    void doesNotRecognizeAnotherUserAsTheResourceOwner() {
        authenticate(new AuthenticatedUser(UUID.randomUUID(), "customer@example.com", Role.USER));

        assertFalse(authorizationService.isOwner(UUID.randomUUID()));
    }

    @Test
    void recognizesOrderOwnerUsingTheOrderRelationship() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticate(new AuthenticatedUser(userId, "customer@example.com", Role.USER));
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(true);

        assertTrue(authorizationService.isOrderOwner(orderId));
    }

    @Test
    void rejectsNonOwnerUsingTheOrderRelationship() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticate(new AuthenticatedUser(userId, "customer@example.com", Role.USER));
        when(orderRepository.existsByIdAndUserId(orderId, userId)).thenReturn(false);

        assertFalse(authorizationService.isOrderOwner(orderId));
    }

    @Test
    void rejectsOrderOwnershipWithoutAuthenticatedPrincipal() {
        assertFalse(authorizationService.isOrderOwner(UUID.randomUUID()));
    }

    @Test
    void doesNotMisclassifyAdminAsOrderOwner() {
        UUID adminId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticate(new AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN));
        when(orderRepository.existsByIdAndUserId(orderId, adminId)).thenReturn(false);

        assertFalse(authorizationService.isOrderOwner(orderId));
    }

    private void authenticate(AuthenticatedUser principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of())
        );
    }
}
