package com.e.commerce.service;

import com.e.commerce.dto.request.ProductRequest;
import com.e.commerce.dto.response.CategoryResponse;
import com.e.commerce.dto.response.ProductResponse;
import com.e.commerce.entity.Category;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.exception.DatabaseException;
import com.e.commerce.exception.ResourceNotFoundException;
import com.e.commerce.repository.CategoryRepository;
import com.e.commerce.repository.ProductRepository;
import com.e.commerce.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gerencia operações de catálogo de produtos e validações de categorias.
 *
 * <p>Responsável por:
 * <ul>
 *   <li>Listar, buscar, criar, atualizar e deletar produtos</li>
 *   <li>Validar integridade de categorias vinculadas</li>
 *   <li>Proteger contra deleção de produtos com pedidos vinculados</li>
 *   <li>Registrar logs de operações críticas</li>
 * </ul>
 *
 * <p>Todas as operações são transacionais para garantir consistência dos dados.
 * Validações ocorrem tanto em nível de aplicação quanto de banco de dados.
 *
 * @author E-Commerce Team
 * @version 1.0
 * @since 2026-04-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StockRepository stockRepository;

    /**
     * Lista todos os produtos com paginação.
     *
     * @param pageable configuração de paginação (página, tamanho, ordenação)
     * @return {@link Page} contendo produtos da página solicitada
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Pageable pageable) {
        log.debug("Listando produtos - página: {}, tamanho: {}", 
            pageable.getPageNumber(), pageable.getPageSize());
        return productRepository.findAll(pageable)
                .map(this::toResponse);
    }

    /**
     * Busca um produto pelo ID.
     *
     * @param id identificador único do produto (UUID)
     * @return {@link ProductResponse} com dados do produto
     * @throws ResourceNotFoundException se produto não for encontrado
     */
    @Transactional(readOnly = true)
    public ProductResponse findById(UUID id) {
        log.debug("Buscando produto por ID: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Produto não encontrado: {}", id);
                    return new ResourceNotFoundException("Produto nao encontrado");
                });
        return toResponse(product);
    }

    /**
     * Cria um novo produto com categorias associadas.
     *
     * <p>Validações:
     * <ul>
     *   <li>Nome do produto deve estar preenchido e ter tamanho válido</li>
     *   <li>Descrição é obrigatória</li>
     *   <li>Preço deve ser positivo</li>
     *   <li>Todas as categorias informadas devem existir no banco</li>
     *   <li>Mínimo 1 categoria deve ser associada</li>
     * </ul>
     *
     * @param request {@link ProductRequest} contendo dados do novo produto
     * @return {@link ProductResponse} com o produto criado (incluindo ID gerado)
     * @throws DatabaseException se violação de constraint ou erro de banco
     * @throws ResourceNotFoundException se alguma categoria não existir
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        log.info("Criando novo produto - nome: {}, categorias: {}", 
            request.getName(), Arrays.toString(request.getCategories()));
        
        try {
            Product product = new Product();
            copyRequestToEntity(request, product);
            
            log.debug("Validações passaram, salvando produto: {}", request.getName());
            Product saved = productRepository.save(product);
            stockRepository.save(Stock.criar(saved, 0));
            
            log.info("Produto criado com sucesso - ID: {}, Nome: {}", 
                saved.getId(), saved.getName());
            return toResponse(saved);
            
        } catch (Exception e) {
            log.error("Erro ao criar produto", e);
            throw new DatabaseException("Erro ao criar produto: " + e.getMessage());
        }
    }

    /**
     * Atualiza um produto existente.
     *
     * <p>Permite alteração de:
     * <ul>
     *   <li>Nome, descrição, preço e URL da imagem</li>
     *   <li>Categorias associadas (substitui completamente)</li>
     * </ul>
     *
     * @param id identificador do produto a atualizar
     * @param request {@link ProductRequest} com novos dados
     * @return {@link ProductResponse} com produto atualizado
     * @throws ResourceNotFoundException se produto ou categoria não existir
     */
    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        log.info("Atualizando produto - ID: {}, novo nome: {}", id, request.getName());
        
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Produto não encontrado para atualização: {}", id);
                    return new ResourceNotFoundException("Produto nao encontrado");
                });

        copyRequestToEntity(request, product);
        Product updated = productRepository.save(product);
        
        log.info("Produto atualizado com sucesso - ID: {}", id);
        return toResponse(updated);
    }

    /**
     * Deleta um produto pelo ID.
     *
     * <p>Proteções:
     * <ul>
     *   <li>Valida existência do produto antes de deletar</li>
     *   <li>Impede deleção se houver pedidos (itens) vinculados</li>
     *   <li>Registra tentativa de deleção em logs</li>
     * </ul>
     *
     * @param id identificador do produto a deletar
     * @throws ResourceNotFoundException se produto não existir
     * @throws DatabaseException se houver integridade de dados violada (ex: pedidos vinculados)
     */
    @Transactional
    public void delete(UUID id) {
        log.warn("Deletando produto - ID: {}", id);
        
        if (!productRepository.existsById(id)) {
            log.error("Tentativa de deletar produto inexistente: {}", id);
            throw new ResourceNotFoundException("Produto nao encontrado");
        }

        try {
            productRepository.deleteById(id);
            log.info("Produto deletado com sucesso - ID: {}", id);
            
        } catch (DataIntegrityViolationException e) {
            log.error("Não é possível deletar produto com pedidos vinculados - ID: {}", id);
            throw new DatabaseException("Produto nao pode ser removido pois esta vinculado a pedidos");
        }
    }

    /**
     * Copia dados do request para a entidade de produto e resolve categorias por nome.
     *
     * <p>Operações:
     * <ul>
     *   <li>Mapeia campos básicos (nome, descrição, preço, imagem)</li>
     *   <li>Processa array de nomes de categorias</li>
     *   <li>Remove espaços em branco e filtro nomes vazios</li>
     *   <li>Valida que todas as categorias existem no banco</li>
     *   <li>Garante mínimo 1 categoria associada</li>
     * </ul>
     *
     * @param request dados de entrada do produto
     * @param product entidade de produto a ser preenchida
     * @throws ResourceNotFoundException se alguma categoria não existir
     * @throws DatabaseException se nenhuma categoria válida for fornecida
     */
    private void copyRequestToEntity(ProductRequest request, Product product) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setImageUrl(request.getImageUrl());

        Set<Category> categories = new LinkedHashSet<>();
        Arrays.stream(request.getCategories())
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .forEach(name -> {
                    Category category = categoryRepository.findByNameIgnoreCase(name)
                            .orElseThrow(() -> {
                                log.error("Categoria não encontrada: {}", name);
                                return new ResourceNotFoundException("Categoria nao encontrada: " + name);
                            });
                    categories.add(category);
                });

        if (categories.isEmpty()) {
            log.error("Nenhuma categoria válida fornecida para o produto");
            throw new DatabaseException("Informe ao menos uma categoria valida");
        }

        product.setCategories(categories);
    }

    /**
     * Converte a entidade de produto para o contrato de resposta da API.
     *
     * <p>Mapeia:
     * <ul>
     *   <li>ID, nome, descrição, preço e URL da imagem do produto</li>
     *   <li>Lista de categorias associadas (apenas dados públicos)</li>
     * </ul>
     *
     * @param product entidade de produto do banco de dados
     * @return {@link ProductResponse} com dados para exposição via API
     */
    private ProductResponse toResponse(Product product) {
        List<CategoryResponse> categories = product.getCategories().stream()
                .map(category -> new CategoryResponse(category.getId(), category.getName()))
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getImageUrl(),
                categories
        );
    }
}

