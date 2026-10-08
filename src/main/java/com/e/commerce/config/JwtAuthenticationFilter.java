package com.e.commerce.config;

import com.e.commerce.service.JwtService;
import com.e.commerce.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro que valida JWT (JSON Web Token) antes de processar requisição.
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>Extrair token do header Authorization (formato: Bearer &lt;token&gt;)</li>
 *   <li>Validar assinatura e expiração do token</li>
 *   <li>Buscar usuário correspondente no banco</li>
 *   <li>Estabelecer contexto de segurança (SecurityContext)</li>
 *   <li>Permitir execução do endpoint se token válido</li>
 *   <li>Bloquear requisição se token inválido/expirado</li>
 * </ul>
 *
 * <p>Flow:
 * <ol>
 *   <li>Cliente inclui: Authorization: Bearer &lt;jwt_token&gt;</li>
 *   <li>Filtro extrai o token do header</li>
 *   <li>Valida usando JwtService</li>
 *   <li>Se válido: busca User no banco e cria Authentication</li>
 *   <li>Se inválido: passa para próximo filtro (será rejeitado por @PreAuthorize)</li>
 * </ol>
 *
 * <p>Segurança:
 * <ul>
 *   <li>Valida assinatura HMAC do token</li>
 *   <li>Verifica expiração do token</li>
 *   <li>Confirma que email no token corresponde ao usuário no banco</li>
 * </ul>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    /**
     * Processa o filtro uma única vez por requisição.
     *
     * <p>Extrai, valida e processa JWT do header Authorization.
     * Se token válido, estabelece contexto de segurança para a requisição.
     *
     * @param request requisição HTTP
     * @param response resposta HTTP
     * @param filterChain cadeia de filtros
     * @throws ServletException se erro ao processar
     * @throws IOException se erro de I/O
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("Header Authorization não encontrado ou formato inválido");
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            log.debug("Token JWT recebido, validando...");
            String email = jwtService.extractUsername(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                if (jwtService.isTokenValid(token, email)) {
                    log.info("Token JWT valido");

                    AuthenticatedUser user = new AuthenticatedUser(
                            jwtService.extractUserId(token),
                            email,
                            jwtService.extractRole(token)
                    );
                    
                    var authToken = new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name()))
                    );

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("SecurityContext autenticado");
                } else {
                    log.warn("Token JWT invalido");
                    SecurityContextHolder.clearContext();
                }
            }
        } catch (Exception e) {
            log.debug("Token JWT invalido");
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}

