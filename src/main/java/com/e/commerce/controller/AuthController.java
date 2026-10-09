package com.e.commerce.controller;

import com.e.commerce.dto.request.LoginRequest;
import com.e.commerce.dto.request.UserRequest;
import com.e.commerce.dto.response.LoginResponse;
import com.e.commerce.dto.response.UserResponse;
import com.e.commerce.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API REST pública de autenticação.
 *
 * <p>Endpoints para:
 * <ul>
 *   <li>Login com e-mail e senha (retorna JWT token)</li>
 *   <li>Registro de novo usuário</li>
 * </ul>
 *
 * <p>Todos os endpoints são públicos (sem autenticação requerida).
 *
 * <p>Flow de Autenticação:
 * <ol>
 *   <li>Novo usuário: POST /auth/register</li>
 *   <li>Usuário existente: POST /auth/login com credenciais</li>
 *   <li>Recebe JWT token válido por 1 hora</li>
 *   <li>Inclui token em header: Authorization: Bearer &lt;token&gt;</li>
 * </ol>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Realiza a autenticação com e-mail e senha.
     *
     * <p>Valida credenciais e retorna JWT token se autenticação bem-sucedida.
     * Token pode ser usado em requisições posteriores via header Authorization.
     *
     * @param request {@link LoginRequest} contendo email e senha
     * @return {@link ResponseEntity} com HTTP 200 (OK) e {@link LoginResponse} contendo JWT token
     * @throws UnauthorizedException se email ou senha inválidos (401)
     * @throws MethodArgumentNotValidException se validação falhar (422)
     *
     * @example
     * POST /auth/login
     * Content-Type: application/json
     *
     * {
     *   "email": "usuario@example.com",
     *   "password": "Senha@123"
     * }
     *
     * 200 OK
     * {
     *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
     *   "type": "Bearer",
     *   "expiresIn": 3600000
     * }
     *
     * Uso posterior:
     * Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Registra um novo usuário no sistema.
     *
     * <p>Cria novo usuário com role padrão USER.
     * Senha é armazenada com hash BCrypt.
     * Email deve ser único.
     *
     * @param request {@link UserRequest} com dados cadastrais
     * @return {@link ResponseEntity} com HTTP 201 (CREATED) e dados do novo usuário
     * @throws DatabaseException se email já existe (409)
     * @throws MethodArgumentNotValidException se validação falhar (422)
     *
     * @example
     * POST /auth/register
     * Content-Type: application/json
     *
     * {
     *   "name": "João da Silva",
     *   "email": "joao@example.com",
     *   "password": "Senha@123",
     *   "phone": "11987654321"
     * }
     *
     * 201 Created
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "name": "João da Silva",
     *   "email": "joao@example.com",
     *   "phone": "11987654321"
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

