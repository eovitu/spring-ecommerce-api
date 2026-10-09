package com.e.commerce.controller;

import com.e.commerce.dto.request.CategoryRequest;
import com.e.commerce.dto.response.CategoryResponse;
import com.e.commerce.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * API REST para gerenciar categorias de produtos.
 *
 * <p>Fornece operações CRUD para categorias:
 * <ul>
 *   <li>GET /api/v1/categories - Listar todas (público)</li>
 *   <li>GET /api/v1/categories/{id} - Buscar por ID (público)</li>
 *   <li>POST /api/v1/categories - Criar nova (requer ADMIN)</li>
 *   <li>PUT /api/v1/categories/{id} - Atualizar (requer ADMIN)</li>
 *   <li>DELETE /api/v1/categories/{id} - Deletar (requer ADMIN)</li>
 * </ul>
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Lista todas as categorias disponíveis.
     *
     * @return {@link ResponseEntity} contendo lista de categorias
     */
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> findAll() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    /**
     * Busca uma categoria por ID.
     *
     * @param id identificador da categoria
     * @return {@link ResponseEntity} com a categoria encontrada
     */
    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    /**
     * Cria uma nova categoria.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * @param request dados da nova categoria
     * @return {@link ResponseEntity} com HTTP 201 (CREATED)
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Atualiza uma categoria existente.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * @param id identificador da categoria
     * @param request novos dados
     * @return {@link ResponseEntity} com categoria atualizada
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CategoryResponse> update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    /**
     * Deleta uma categoria.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * @param id identificador da categoria
     * @return {@link ResponseEntity} com HTTP 204 (NO_CONTENT)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
