package com.e.commerce.service;

import com.e.commerce.dto.request.ProductRequest;
import com.e.commerce.entity.Category;
import com.e.commerce.entity.Product;
import com.e.commerce.entity.Stock;
import com.e.commerce.repository.CategoryRepository;
import com.e.commerce.repository.ProductRepository;
import com.e.commerce.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductInventoryServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private StockRepository stockRepository;

    @Test
    void deletesStockBeforeProductAndFlushesIntegrityChecks() {
        UUID id = UUID.randomUUID(); Product product = new Product();
        Stock stock = Stock.criar(product, 0);
        when(productRepository.existsById(id)).thenReturn(true);
        when(stockRepository.findByProductIdForUpdate(id)).thenReturn(Optional.of(stock));
        new ProductService(productRepository, categoryRepository, stockRepository).delete(id);
        var ordered = org.mockito.Mockito.inOrder(stockRepository, productRepository);
        ordered.verify(stockRepository).findByProductIdForUpdate(id);
        ordered.verify(stockRepository).delete(stock);
        ordered.verify(stockRepository).flush();
        ordered.verify(productRepository).deleteById(id);
        ordered.verify(productRepository).flush();
    }

    @Test
    void historicalReservationPreventsProductDeletionAtStockFlush() {
        UUID id = UUID.randomUUID();
        Stock stock = Stock.criar(new Product(), 0);
        when(productRepository.existsById(id)).thenReturn(true);
        when(stockRepository.findByProductIdForUpdate(id)).thenReturn(Optional.of(stock));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("history FK"))
                .when(stockRepository).flush();
        org.junit.jupiter.api.Assertions.assertThrows(com.e.commerce.exception.DatabaseException.class,
                () -> new ProductService(productRepository, categoryRepository, stockRepository).delete(id));
        org.mockito.Mockito.verify(productRepository, org.mockito.Mockito.never()).deleteById(id);
    }

    @Test
    void newProductStartsWithZeroStockInsteadOfMissingStockRow() {
        Category category = new Category();
        category.setName("Category");
        when(categoryRepository.findByNameIgnoreCase("Category")).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(UUID.randomUUID());
            return product;
        });
        ProductRequest request = new ProductRequest(
                "Product",
                "Description",
                BigDecimal.TEN,
                "https://example.com/image.png",
                new String[]{"Category"}
        );
        ProductService service = new ProductService(productRepository, categoryRepository, stockRepository);

        service.create(request);

        ArgumentCaptor<Stock> stockCaptor = ArgumentCaptor.forClass(Stock.class);
        verify(stockRepository).save(stockCaptor.capture());
        assertEquals(0, stockCaptor.getValue().getTotalQuantity());
        assertEquals(0, stockCaptor.getValue().getReservedQuantity());
    }
}
