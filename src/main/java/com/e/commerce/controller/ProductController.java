package com.e.commerce.controller;

import com.e.commerce.dto.request.ProductRequest;
import com.e.commerce.dto.response.ProductResponse;
import com.e.commerce.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * API REST para gerenciar produtos do catálogo de e-commerce.
 *
 * <p>Fornece operações CRUD para produtos:
 * <ul>
 *   <li>GET /api/v1/products - Listar todos (público, com paginação)</li>
 *   <li>GET /api/v1/products/{id} - Buscar por ID (público)</li>
 *   <li>POST /api/v1/products - Criar novo (requer role ADMIN)</li>
 *   <li>PUT /api/v1/products/{id} - Atualizar (requer role ADMIN)</li>
 *   <li>DELETE /api/v1/products/{id} - Deletar (requer role ADMIN)</li>
 * </ul>
 *
 * <p>Autenticação: Endpoints GET são públicos. POST/PUT/DELETE requerem token JWT com role ADMIN.
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Lista todos os produtos com paginação.
     *
     * @param page número da página (começa em 0, padrão: 0)
     * @param size quantidade de produtos por página (padrão: 20)
     * @return {@link Page} contendo produtos da página solicitada
     *
     * @example
     * GET /api/v1/products?page=0&size=20
     * 200 OK
     * {
     *   "content": [
     *     {
     *       "id": "550e8400-e29b-41d4-a716-446655440000",
     *       "name": "Product Name",
     *       "price": 99.90,
     *       "categories": [...]
     *     }
     *   ],
     *   "totalElements": 150,
     *   "totalPages": 8,
     *   "currentPage": 0,
     *   "pageSize": 20
     * }
     */
    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size <= 0) {
            throw new com.e.commerce.exception.InvalidRequestException("Paginacao invalida");
        }
        return ResponseEntity.ok(productService.findAll(PageRequest.of(page, size)));
    }

    /**
     * Busca um produto por ID.
     *
     * @param id identificador único do produto (UUID)
     * @return {@link ResponseEntity} com o produto encontrado
     * @throws ResourceNotFoundException se produto não for encontrado
     *
     * @example
     * GET /api/v1/products/550e8400-e29b-41d4-a716-446655440000
     * 200 OK
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "name": "Product Name",
     *   "description": "Product description",
     *   "price": 99.90,
     *   "imageUrl": "https://...",
     *   "categories": [{"id": "...", "name": "Electronics"}]
     * }
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    /**
     * Cria um novo produto no catálogo.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * <p>Validações aplicadas:
     * <ul>
     *   <li>name: 2-120 caracteres, obrigatório</li>
     *   <li>description: 5-500 caracteres, obrigatório</li>
     *   <li>price: maior que 0.01, obrigatório</li>
     *   <li>imageUrl: máximo 500 caracteres, obrigatório</li>
     *   <li>categories: mínimo 1, máximo ilimitado, obrigatório</li>
     * </ul>
     *
     * @param request {@link ProductRequest} com dados do novo produto
     * @return {@link ResponseEntity} com HTTP 201 (CREATED) e produto criado
     * @throws MethodArgumentNotValidException se validação falhar (422)
     * @throws ResourceNotFoundException se alguma categoria não existir (404)
     * @throws DatabaseException se violação de constraint no banco (409)
     * @throws UnauthorizedException se não autenticado (401)
     * @throws ForbiddenException se não é ADMIN (403)
     *
     * @example
     * POST /api/v1/products
     * Authorization: Bearer eyJhbGc...
     * Content-Type: application/json
     * {
     *   "name": "Product Name",
     *   "description": "Product description here",
     *   "price": 99.90,
     *   "imageUrl": "https://example.com/image.jpg",
     *   "categories": ["Electronics", "Gadgets"]
     * }
     *
     * 201 Created
     * {
     *   "id": "550e8400-e29b-41d4-a716-446655440000",
     *   "name": "Product Name",
     *   "price": 99.90,
     *   "categories": [{"id": "...", "name": "Electronics"}]
     * }
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Atualiza um produto existente.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * @param id identificador do produto a atualizar
     * @param request {@link ProductRequest} com novos dados
     * @return {@link ResponseEntity} com produto atualizado
     * @throws ResourceNotFoundException se produto não existir (404)
     * @throws ForbiddenException se não é ADMIN (403)
     * @throws MethodArgumentNotValidException se validação falhar (422)
     *
     * @example
     * PUT /api/v1/products/550e8400-e29b-41d4-a716-446655440000
     * Authorization: Bearer eyJhbGc...
     * Content-Type: application/json
     * {
     *   "name": "Updated Name",
     *   "description": "Updated description",
     *   "price": 129.90,
     *   "imageUrl": "https://example.com/new-image.jpg",
     *   "categories": ["Electronics"]
     * }
     *
     * 200 OK
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    /**
     * Deleta um produto do catálogo.
     *
     * <p><b>Requer autenticação com role ADMIN.</b>
     *
     * <p>Proteções:
     * <ul>
     *   <li>Produto não pode estar vinculado a nenhum pedido</li>
     *   <li>Retorna 409 (CONFLICT) se houver violação de integridade</li>
     * </ul>
     *
     * @param id identificador do produto a deletar
     * @return {@link ResponseEntity} com HTTP 204 (NO_CONTENT) se sucesso
     * @throws ResourceNotFoundException se produto não existir (404)
     * @throws DatabaseException se produto tem pedidos vinculados (409)
     * @throws ForbiddenException se não é ADMIN (403)
     *
     * @example
     * DELETE /api/v1/products/550e8400-e29b-41d4-a716-446655440000
     * Authorization: Bearer eyJhbGc...
     *
     * 204 No Content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
