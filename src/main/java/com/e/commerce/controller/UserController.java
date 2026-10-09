package com.e.commerce.controller;

import com.e.commerce.dto.request.UserRequest;
import com.e.commerce.dto.response.UserResponse;
import com.e.commerce.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API REST para gerenciar usuários do sistema.
 *
 * <p>Fornece operações CRUD para usuários:
 * <ul>
 *   <li>GET /api/v1/users - Listar todos (requer ADMIN)</li>
 *   <li>GET /api/v1/users/{id} - Buscar por ID (requer ADMIN ou próprio usuário)</li>
 *   <li>POST /api/v1/users - Criar novo (requer ADMIN)</li>
 *   <li>PUT /api/v1/users/{id} - Atualizar (requer ADMIN ou próprio usuário)</li>
 *   <li>DELETE /api/v1/users/{id} - Deletar (requer ADMIN)</li>
 * </ul>
 *
 * <p>Autenticação: Todos os endpoints requerem autenticação com role ADMIN,
 * exceto GET/PUT que permitem usuário acessar seus próprios dados.
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Lista todos os usuários do sistema.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * @return {@link ResponseEntity} contendo lista de usuários
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    /**
     * Busca um usuário por ID.
     *
     * <p><b>Requer autenticação.</b> ADMIN pode buscar qualquer usuário.
     * Usuário comum pode buscar apenas seu próprio perfil.
     *
     * @param id identificador do usuário
     * @return {@link ResponseEntity} com dados do usuário
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOwner(#id)")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    /**
     * Cria um novo usuário.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * <p>Validações:
     * <ul>
     *   <li>Email deve ser único</li>
     *   <li>Senha deve atender critérios de segurança</li>
     *   <li>Novo usuário recebe role USER por padrão</li>
     * </ul>
     *
     * @param request {@link UserRequest} com dados do novo usuário
     * @return {@link ResponseEntity} com HTTP 201 (CREATED)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        UserResponse response = userService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Atualiza um usuário existente.
     *
     * <p><b>Requer autenticação.</b> ADMIN pode atualizar qualquer usuário.
     * Usuário comum pode atualizar apenas seu próprio perfil.
     *
     * @param id identificador do usuário
     * @param request novos dados do usuário
     * @return {@link ResponseEntity} com usuário atualizado
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @authorizationService.isOwner(#id)")
    public ResponseEntity<UserResponse> update(@PathVariable UUID id, @Valid @RequestBody UserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    /**
     * Deleta um usuário.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * <p>Proteções:
     * <ul>
     *   <li>Usuário não pode ter pedidos pendentes</li>
     *   <li>Retorna 409 (CONFLICT) se houver pedidos vinculados</li>
     * </ul>
     *
     * @param id identificador do usuário a deletar
     * @return {@link ResponseEntity} com HTTP 204 (NO_CONTENT)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
