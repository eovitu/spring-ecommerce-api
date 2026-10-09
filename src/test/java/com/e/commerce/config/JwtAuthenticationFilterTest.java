package com.e.commerce.config;

import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import com.e.commerce.repository.UserRepository;
import com.e.commerce.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    @Mock JwtService jwt;
    @Mock UserRepository users;
    @Mock FilterChain chain;
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private User validClaims() {
        User u=new User(); u.setId(UUID.randomUUID()); u.setEmail("private-customer@example.com"); u.setRole(Role.USER);
        request.addHeader("Authorization","Bearer private-token");
        when(jwt.extractUsername("private-token")).thenReturn(u.getEmail());
        when(jwt.extractUserId("private-token")).thenReturn(u.getId());
        when(jwt.extractRole("private-token")).thenReturn(u.getRole());
        when(jwt.extractSessionVersion("private-token")).thenReturn(0L);
        when(jwt.isTokenValid("private-token",u.getEmail())).thenReturn(true);
        return u;
    }
    private void run() throws Exception { new JwtAuthenticationFilter(jwt,users).doFilterInternal(request,response,chain); }
    @Test void validTokenUsesPersistedIdentityWithoutLoggingIt() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            User u=validClaims();when(users.findById(u.getId())).thenReturn(Optional.of(u));run();
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());verify(chain).doFilter(request,response);
            assertTrue(appender.list.stream().noneMatch(e->e.getFormattedMessage().contains("private-")));
        } finally { logger.detachAppender(appender);appender.stop(); }
    }
    @Test void revokedVersionIs401() throws Exception {
        User u=validClaims();u.setSessionVersion(1);when(users.findById(u.getId())).thenReturn(Optional.of(u));run();
        assertEquals(401,response.getStatus());assertNull(SecurityContextHolder.getContext().getAuthentication());verifyNoInteractions(chain);
    }
    @Test void missingUserIs401() throws Exception {
        User u=validClaims();when(users.findById(u.getId())).thenReturn(Optional.empty());run();assertEquals(401,response.getStatus());
    }
    @Test void databaseFailureIs503AndNeverAuthenticates() throws Exception {
        User u=validClaims();when(users.findById(u.getId())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private-db"));
        run();assertEquals(503,response.getStatus());assertFalse(response.getContentAsString().contains("private"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());verifyNoInteractions(chain);
    }
    @Test void connectionTransactionFailureIs503() throws Exception {
        User u=validClaims();when(users.findById(u.getId())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("private-db"));
        run();assertEquals(503,response.getStatus());assertNull(SecurityContextHolder.getContext().getAuthentication());verifyNoInteractions(chain);
    }
    @Test void invalidTokenDetailsAreNotExposed() throws Exception {
        request.addHeader("Authorization","Bearer private-token");when(jwt.extractUsername("private-token")).thenThrow(new IllegalArgumentException("private-claim"));
        run();assertEquals(401,response.getStatus());assertFalse(response.getContentAsString().contains("private"));verifyNoInteractions(users,chain);
    }
    @Test void doesNotExecuteChainTwiceWhenDownstreamFails() throws Exception {
        User u=validClaims();when(users.findById(u.getId())).thenReturn(Optional.of(u));
        doThrow(new ServletException("downstream")).when(chain).doFilter(request,response);
        assertThrows(ServletException.class,this::run);verify(chain,times(1)).doFilter(request,response);
    }
    @Test void anonymousRequestPreservesChain() throws Exception { run();verify(chain).doFilter(request,response);verifyNoInteractions(users,jwt); }
}
