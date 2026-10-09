package com.e.commerce.service;

import com.e.commerce.dto.request.UserRequest;
import com.e.commerce.dto.response.UserResponse;
import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import com.e.commerce.exception.DatabaseException;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementa as regras de negócio de usuários e protege a integridade dos dados.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse create(UserRequest request) {
        validateEmailUniqueness(request.getEmail(), null);

        User user = new User();
        copyRequestToEntity(request, user);
        user.setRole(Role.USER);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario nao encontrado"));

        validateEmailUniqueness(request.getEmail(), id);
        copyRequestToEntity(request, user);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuario nao encontrado");
        }

        try {
            userRepository.deleteById(id);
        } catch (DataIntegrityViolationException e) {
            throw new DatabaseException("Usuario nao pode ser removido pois possui registros vinculados");
        }
    }

    /**
     * Garante a unicidade do e-mail no contexto de criação e atualização.
     */
    private void validateEmailUniqueness(String email, UUID currentUserId) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            if (currentUserId == null || !existing.getId().equals(currentUserId)) {
                throw new DatabaseException("Email ja cadastrado");
            }
        });
    }

    /**
     * Copia os campos editáveis do request para a entidade persistente.
     */
    private void copyRequestToEntity(UserRequest request, User user) {
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }
    }

    /**
     * Converte a entidade em DTO sem expor atributos sensíveis.
     */
    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone());
    }
}
