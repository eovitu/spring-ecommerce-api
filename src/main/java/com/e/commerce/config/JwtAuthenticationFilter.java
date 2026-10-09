package com.e.commerce.config;

import com.e.commerce.service.JwtService;
import com.e.commerce.repository.UserRepository;
import com.e.commerce.security.AuthenticatedUser;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

/** Valida assinatura e sessão persistida antes de estabelecer identidade. */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String email;
        java.util.UUID id;
        com.e.commerce.enums.Role role;
        long version;
        try {
            String token = header.substring(7);
            email = jwtService.extractUsername(token);
            id = jwtService.extractUserId(token);
            role = jwtService.extractRole(token);
            version = jwtService.extractSessionVersion(token);
            if (email == null || !jwtService.isTokenValid(token, email)) {
                reject(response, 401);
                return;
            }
        } catch (JwtException | IllegalArgumentException e) {
            reject(response, 401);
            return;
        }
        try {
            var current = userRepository.findById(id).orElse(null);
            if (current == null || current.getSessionVersion() != version
                    || !email.equals(current.getEmail()) || current.getRole() != role) {
                reject(response, 401);
                return;
            }
            var principal = new AuthenticatedUser(current.getId(), current.getEmail(), current.getRole());
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))));
        } catch (DataAccessException | org.springframework.transaction.TransactionException e) {
            reject(response, 503);
            return;
        }
        // Do not catch downstream errors or execute the chain twice.
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(status == 401
            ? "{\"message\":\"Credenciais invalidas\"}"
            : "{\"message\":\"Servico temporariamente indisponivel\"}");
    }
}
