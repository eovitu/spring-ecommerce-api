package com.e.commerce.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.e.commerce.controller.ProductController;
import com.e.commerce.entity.Category;
import com.e.commerce.exception.ResourceExceptionHandler;
import com.e.commerce.repository.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FinalCatalogRegressionTest {
    @Test void databaseDetailsStayOutOfResponseAndLogs() throws Exception {
        var products = mock(ProductRepository.class);
        var categories = mock(CategoryRepository.class);
        var category = new Category(); category.setName("Books");
        when(categories.findAllByNameIgnoreCase("Books")).thenReturn(java.util.List.of(category));
        when(products.save(any())).thenThrow(new DataIntegrityViolationException("INTERNAL_DB_MARKER"));
        var service = new ProductService(products, categories, mock(StockRepository.class));
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ResourceExceptionHandler()).build();
        Logger logger = (Logger) LoggerFactory.getLogger(ProductService.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try {
            mvc.perform(post("/api/v1/products").contentType("application/json").content("""
                {"name":"Product","description":"Description","price":10,"imageUrl":"https://example.com/a","categories":["Books"]}
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Erro ao criar produto"));
            assertTrue(appender.list.stream().noneMatch(event ->
                    event.getFormattedMessage().contains("INTERNAL_DB_MARKER") || event.getThrowableProxy() != null));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void invalidCatalogInputsAreClientErrors() throws Exception {
        var service = mock(ProductService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new ResourceExceptionHandler()).build();
        for (String query : new String[]{"?page=-1", "?size=0", "?size=-1"})
            mvc.perform(get("/api/v1/products" + query)).andExpect(status().isUnprocessableContent());
        for (String path : new String[]{"?page=abc", "/not-a-uuid"})
            mvc.perform(get("/api/v1/products" + path)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Parametro da requisicao invalido"));
        verifyNoInteractions(service);
    }
}
