package com.e.commerce.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuração centralizada de segurança da aplicação.
 *
 * <p>Responsável por:
 * <ul>
 *   <li>Codificação segura de senhas (BCrypt)</li>
 *   <li>Validação de tokens JWT (JwtAuthenticationFilter)</li>
 *   <li>Autorização por role (@PreAuthorize)</li>
 *   <li>Proteção contra CSRF (desabilitado para API stateless)</li>
 *   <li>Configuração de CORS</li>
 *   <li>Gerenciamento de sessão (stateless)</li>
 * </ul>
 *
 * <p>Flow de Segurança:
 * <ol>
 *   <li>Cliente envia requisição com JWT no header Authorization</li>
 *   <li>JwtAuthenticationFilter valida o token</li>
 *   <li>SecurityContext é populado com usuário autenticado</li>
 *   <li>@PreAuthorize verifica autorização (role)</li>
 *   <li>Controller executa ou retorna 403 Forbidden</li>
 * </ol>
 *
 * <p>Endpoints Públicos (sem autenticação):
 * <ul>
 *   <li>POST /auth/login - Autenticação</li>
 *   <li>POST /auth/register - Registro de novo usuário</li>
 *   <li>GET /api/v1/products - Listar produtos</li>
 *   <li>GET /api/v1/categories - Listar categorias</li>
 * </ul>
 *
 * <p>Endpoints Protegidos (requerem token e role):
 * <ul>
 *   <li>POST /api/v1/products - Requer ADMIN</li>
 *   <li>PUT /api/v1/products/{id} - Requer ADMIN</li>
 *   <li>DELETE /api/v1/products/{id} - Requer ADMIN</li>
 *   <li>Similares para categories, users, orders, payments</li>
 * </ul>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;

  /**
   * Fornece o codificador de senhas padrão da aplicação.
   *
   * <p>BCrypt é recomendado para armazenamento seguro de senhas porque:
   * <ul>
   *   <li>Inclui salt aleatório (evita rainbow tables)</li>
   *   <li>Tem custo computacional configurável (resiste a força bruta)</li>
   *   <li>É lento de propósito (torna ataques computacionalmente caros)</li>
   * </ul>
   *
   * <p>Exemplo de uso:
   * <pre>
   * {@code
   * passwordEncoder.encode("senhaPlainText") // Retorna: $2a$10$... (hash)
   * passwordEncoder.matches("senhaPlainText", hashArmazenado) // Retorna: true/false
   * }
   * </pre>
   *
   * @return {@link PasswordEncoder} configurado com BCrypt
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Define a cadeia de filtros HTTP utilizada pelo Spring Security.
   *
   * <p>Configura:
   * <ul>
   *   <li>CORS: Permite requisições de domínios específicos</li>
   *   <li>CSRF: Desabilitado para API stateless (tokens JWT providenciam proteção)</li>
   *   <li>JwtAuthenticationFilter: Valida JWT antes de processar requisição</li>
   *   <li>Autorização HTTP: Define quais endpoints requerem autenticação</li>
   *   <li>Sessão: STATELESS (não mantém estado no servidor)</li>
   * </ul>
   *
   * <p>Ordem dos Filtros (importante):
   * <ol>
   *   <li>JwtAuthenticationFilter é adicionado ANTES de UsernamePasswordAuthenticationFilter</li>
   *   <li>Isto faz com que JWT seja validado em cada requisição</li>
   *   <li>Se inválido, próximos filtros tratam a rejeição</li>
   * </ol>
   *
   * @param http {@link HttpSecurity} para configurar segurança HTTP
   * @return {@link SecurityFilterChain} construída com configurações
   * @throws Exception se erro ao configurar
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Configurar CORS usando bean do CorsConfig
        .cors(cors -> cors.configure(http))
        
        // Desabilitar CSRF - API stateless usa JWT ao invés de CSRF tokens
        .csrf(csrf -> csrf.disable())
        
        // Adicionar JwtAuthenticationFilter ANTES de UsernamePasswordAuthenticationFilter
        // Isto permite que JWT seja validado em cada requisição
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
        
         // Configurar autorização HTTP
         .authorizeHttpRequests(auth -> auth
             // Endpoints públicos (autenticação não requerida)
             .requestMatchers("/auth/**").permitAll()                                    // Login e register
             .requestMatchers(HttpMethod.POST, "/webhooks/payments").permitAll()
             .requestMatchers(HttpMethod.GET, "/api/v1/products/**").permitAll()        // GET produtos - público
             .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()      // GET categorias - público

             // Endpoints protegidos (autenticação requerida)
             // @PreAuthorize nos controllers define quem pode acessar
             .anyRequest().authenticated()
         )

        // Configurar gerenciamento de sessão
        .sessionManagement(session -> session
            // STATELESS: não criar/usar sessões HTTP
            // JWT providencia o estado de autenticação
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

    return http.build();
  }
}
