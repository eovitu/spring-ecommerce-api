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

    @Test
    void invalidTokenDetailsAreNotLogged() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        var originalLevel = logger.getLevel();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            when(request.getHeader("Authorization")).thenReturn("Bearer private-token");
            when(jwtService.extractUsername("private-token"))
                    .thenThrow(new IllegalArgumentException("private-token private-claim"));
            new JwtAuthenticationFilter(jwtService).doFilterInternal(request, response, filterChain);
            org.junit.jupiter.api.Assertions.assertFalse(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("private-")));
            org.junit.jupiter.api.Assertions.assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(filterChain, times(1)).doFilter(request, response);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

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
