package com.e.commerce.service;

import com.e.commerce.dto.request.LoginRequest;
import com.e.commerce.dto.request.UserRequest;
import com.e.commerce.dto.response.LoginResponse;
import com.e.commerce.dto.response.UserResponse;
import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import com.e.commerce.exception.DatabaseException;
import com.e.commerce.exception.LoginRateLimitException;
import com.e.commerce.exception.UnauthorizedException;
import com.e.commerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Orquestra os casos de uso de autenticação da API.
 *
 * <p>Responsável por validar credenciais, emitir token JWT e registrar novos
 * usuários com senha codificada.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttempts;

    /**
     * Autentica o usuário e retorna um token de acesso.
     *
     * @param request credenciais de login
     * @return payload de autenticação com token Bearer
     * @throws UnauthorizedException quando e-mail ou senha forem inválidos
     */
    public LoginResponse login(LoginRequest request) {
        return login(request, "internal");
    }

    public LoginResponse login(LoginRequest request, String remoteAddress) {
        var result = loginAttempts.authenticate(request, remoteAddress);
        if (result.retryAfter() > 0) {
            log.debug("Login blocked by attempt limit");
            throw new LoginRateLimitException(result.retryAfter());
        }
        if (result.response() == null) {
            log.debug("Login rejected: invalid credentials");
            throw new UnauthorizedException("Credenciais invalidas");
        }
        return result.response();
    }

    /** Revoga todas as sessões após adquirir o mesmo lock usado na emissão. */
    @org.springframework.transaction.annotation.Transactional
    public void logout(java.util.UUID userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UnauthorizedException("Credenciais invalidas"));
        user.revokeSessions();
    }

    /**
     * Registra um novo usuário com role padrão de cliente.
     *
     * @param request dados cadastrais do usuário
     * @return representação do usuário criado sem senha
     * @throws DatabaseException quando o e-mail já estiver cadastrado
     */
    public UserResponse register(UserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DatabaseException("Email ja cadastrado");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(Role.USER);

        user = userRepository.save(user);

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setName(user.getName());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());

        return response;
    }
}
