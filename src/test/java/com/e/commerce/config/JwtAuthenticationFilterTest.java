package com.e.commerce.config;

import com.e.commerce.enums.Role;
import com.e.commerce.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doesNotExecuteTheChainTwiceWhenDownstreamFails() throws Exception {
        String token = "valid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn("customer@example.com");
        when(jwtService.isTokenValid(token, "customer@example.com")).thenReturn(true);
        when(jwtService.extractUserId(token)).thenReturn(UUID.randomUUID());
        when(jwtService.extractRole(token)).thenReturn(Role.USER);
        org.mockito.Mockito.doThrow(new ServletException("downstream failure"))
                .when(filterChain).doFilter(request, response);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

        assertThrows(
                ServletException.class,
                () -> filter.doFilterInternal(request, response, filterChain)
        );
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void invalidTokenStillExecutesTheChainOnlyOnce() throws IOException, ServletException {
        String token = "invalid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(new IllegalArgumentException("invalid token"));

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
