package com.e.commerce.integration;

import com.e.commerce.dto.request.OrderItemRequest;
import com.e.commerce.dto.request.OrderRequest;
import com.e.commerce.dto.response.OrderResponse;
import com.e.commerce.exception.InsufficientStockException;
import com.e.commerce.service.OrderService;
import com.e.commerce.service.OutboxPublisher;
import com.e.commerce.service.PaymentWebhookService;
import com.e.commerce.service.StockReservationExpirationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.dynamic=false",
        "jobs.stock-expiration.initial-delay=3600000",
        "jobs.outbox.initial-delay=3600000"
})
@Testcontainers
class InventoryConcurrencyPostgresTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentWebhookService paymentWebhookService;
    @Autowired
    private StockReservationExpirationService expirationService;
    @Autowired
    private OutboxPublisher outboxPublisher;

    @org.springframework.boot.test.web.server.LocalServerPort
    private int port;
    @Autowired private com.e.commerce.service.JwtService jwtService;
    @Autowired private com.e.commerce.service.ProductService productService;

    private UUID userId;
    private UUID productId;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.secret-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        registry.add("security.webhook.secret", () -> "integration-webhook-secret");
        registry.add("spring.rabbitmq.password", () -> "integration-rabbit-password");
        registry.add("spring.rabbitmq.port", () -> "1");
    }

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute("DELETE FROM webhook_inbox");
        jdbcTemplate.execute("DELETE FROM stock_reservation");
        jdbcTemplate.execute("DELETE FROM outbox_event");
        jdbcTemplate.execute("DELETE FROM payment");
        jdbcTemplate.execute("DELETE FROM order_item");
        jdbcTemplate.execute("DELETE FROM tb_orders");
        jdbcTemplate.execute("DELETE FROM stock");
        jdbcTemplate.execute("DELETE FROM tb_product_category");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM category");
        jdbcTemplate.execute("DELETE FROM tb_user");

        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
                "INSERT INTO tb_user (id, name, email, password, role, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, "Customer", userId + "@example.com", "hash", "USER", now, now
        );
        jdbcTemplate.update(
                "INSERT INTO product (id, name, description, price, image_url) VALUES (?, ?, ?, ?, ?)",
                productId, "Product", "Description", BigDecimal.TEN, "https://example.com/product.png"
        );
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"Livros", "livros"})
    void ambiguousCategoryIsExplicitClientErrorWithoutChangingData(String duplicate) {
        jdbcTemplate.update("INSERT INTO category (id,name) VALUES (?,?)", UUID.randomUUID(), "Livros");
        jdbcTemplate.update("INSERT INTO category (id,name) VALUES (?,?)", UUID.randomUUID(), duplicate);
        var request = new com.e.commerce.dto.request.ProductRequest("Product", "Description", BigDecimal.TEN,
                "https://example.com/a", new String[]{"Livros"});
        var createError = assertThrows(com.e.commerce.exception.InvalidRequestException.class,
                () -> productService.create(request));
        assertEquals("Nome de categoria ambiguo; informe uma categoria com nome unico", createError.getMessage());
        assertThrows(com.e.commerce.exception.InvalidRequestException.class,
                () -> productService.update(productId, request));
        assertEquals(2, integer("SELECT COUNT(*) FROM category"));
        assertEquals(1, integer("SELECT COUNT(*) FROM product"));
        assertEquals(0, integer("SELECT COUNT(*) FROM tb_product_category"));
    }

    @Test void uniqueCategoryResolvesIgnoringCase() {
        UUID categoryId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO category (id,name) VALUES (?,?)", categoryId, "Livros");
        var request = new com.e.commerce.dto.request.ProductRequest("Product", "Description", BigDecimal.TEN,
                "https://example.com/a", new String[]{"livros"});
        var created = productService.create(request);
        assertEquals(categoryId, created.getCategories().getFirst().getId());
        assertEquals(categoryId, productService.update(productId, request).getCategories().getFirst().getId());
    }
    @Test
    void concurrentCheckoutsNeverReserveMoreThanAvailableStock() throws Exception {
        int availableStock = 3;
        int attempts = 10;
        insertStock(availableStock);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            for (int index = 0; index < attempts; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        orderService.create(orderRequest(), userId);
                        return true;
                    } catch (InsufficientStockException exception) {
                        return false;
                    }
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes++;
                }
            }

            assertEquals(availableStock, successes);
        }

        assertEquals(availableStock, integer("SELECT reserved_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(availableStock, integer("SELECT COUNT(*) FROM stock_reservation WHERE status = 'ATIVA'"));
        assertEquals(availableStock, integer("SELECT COUNT(*) FROM tb_orders"));
    }

    @Test
    void webhookBeforeExpirationConsumesReservationAndStock() {
        insertStock(1);
        UUID orderId = createOrder();

        assertTrue(paymentWebhookService.confirmPayment("evt-before-expiration", orderId));
        assertFalse(paymentWebhookService.confirmPayment("evt-before-expiration", orderId));

        assertEquals("PAGO", text("SELECT status FROM tb_orders WHERE id = ?", orderId));
        assertEquals("CONFIRMADO", text("SELECT status FROM payment WHERE order_id = ?", orderId));
        assertEquals("CONSUMIDA", text("SELECT status FROM stock_reservation WHERE order_id = ?", orderId));
        assertEquals(0, integer("SELECT total_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(0, integer("SELECT reserved_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(1, integer("SELECT COUNT(*) FROM webhook_inbox WHERE event_id = 'evt-before-expiration'"));
    }

    @Test
    void lateWebhookAndExpirationRaceEndsInReconciliationWithoutOverselling() throws Exception {
        insertStock(1);
        UUID orderId = createOrder();
        jdbcTemplate.update(
                "UPDATE stock_reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE order_id = ?",
                orderId
        );
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> expiration = executor.submit(() -> {
                await(start);
                expirationService.expireOrderReservations(orderId);
            });
            Future<?> webhook = executor.submit(() -> {
                await(start);
                paymentWebhookService.confirmPayment("evt-race", orderId);
            });
            start.countDown();
            expiration.get();
            webhook.get();
        }

        assertEquals("RECONCILIACAO_PENDENTE", text("SELECT status FROM tb_orders WHERE id = ?", orderId));
        assertEquals("RECONCILIACAO_PENDENTE", text("SELECT status FROM payment WHERE order_id = ?", orderId));
        assertEquals("EXPIRADA", text("SELECT status FROM stock_reservation WHERE order_id = ?", orderId));
        assertEquals(1, integer("SELECT total_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(0, integer("SELECT reserved_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(1, integer("SELECT COUNT(*) FROM webhook_inbox WHERE event_id = 'evt-race'"));
    }

    @Test
    void outboxRemainsPendingWhenRabbitPublicationFails() {
        insertStock(1);
        UUID orderId = createOrder();
        jdbcTemplate.update(
                "UPDATE stock_reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE order_id = ?",
                orderId
        );

        assertTrue(expirationService.expireOrderReservations(orderId));
        assertEquals("CANCELADO", text("SELECT status FROM tb_orders WHERE id = ?", orderId));
        assertEquals(1, integer("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL", orderId));

        assertThrows(AmqpException.class, outboxPublisher::publishPending);

        assertEquals(1, integer("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL", orderId));
    }

    @Test
    void outboxInsertFailureRollsBackExpirationAndStockRelease() {
        insertStock(1);
        UUID orderId = createOrder();
        jdbcTemplate.update(
                "UPDATE stock_reservation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE order_id = ?",
                orderId
        );
        jdbcTemplate.execute("""
                ALTER TABLE outbox_event
                ADD CONSTRAINT chk_test_reject_expiration_event
                CHECK (event_type <> 'pedido.cancelado.expiracao')
                """);

        try {
            assertThrows(RuntimeException.class, () -> expirationService.expireOrderReservations(orderId));
        } finally {
            jdbcTemplate.execute("ALTER TABLE outbox_event DROP CONSTRAINT chk_test_reject_expiration_event");
        }

        assertEquals("AGUARDANDO_PAGAMENTO", text("SELECT status FROM tb_orders WHERE id = ?", orderId));
        assertEquals("PENDENTE", text("SELECT status FROM payment WHERE order_id = ?", orderId));
        assertEquals("ATIVA", text("SELECT status FROM stock_reservation WHERE order_id = ?", orderId));
        assertEquals(1, integer("SELECT reserved_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(0, integer("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ?", orderId));
    }

    @Test
    void authenticatedOpenApiWorksAndAnonymousDocumentationStaysProtected() throws Exception {
        assertEquals(403, http("GET", "/v3/api-docs", null, false).statusCode());
        var response = http("GET", "/v3/api-docs", null, true);
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"openapi\""));
        assertTrue(response.body().contains("/payments"));
    }

    @Test
    void repeatedAndConcurrentPaymentPostsKeepOnePendingPayment() throws Exception {
        insertStock(1);
        UUID orderId = createOrder();
        String body = "{\"orderId\":\"" + orderId + "\"}";
        var first = http("POST", "/payments", body, true);
        assertEquals(201, first.statusCode(), first.body());
        String original = first.body();
        assertEquals(original, http("POST", "/payments", body, true).body());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(5)) {
            List<Future<java.net.http.HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                futures.add(executor.submit(() -> { await(start); return http("POST", "/payments", body, true); }));
            }
            start.countDown();
            for (var future : futures) {
                var response = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertEquals(201, response.statusCode(), response.body());
                assertEquals(original, response.body());
            }
        }
        assertEquals(1, integer("SELECT COUNT(*) FROM payment WHERE order_id = ?", orderId));
        assertEquals("PENDENTE", text("SELECT status FROM payment WHERE order_id = ?", orderId));
        assertEquals("AGUARDANDO_PAGAMENTO", text("SELECT status FROM tb_orders WHERE id = ?", orderId));
    }

    @Test
    void deletingProductWithoutHistoryAlsoDeletesStock() {
        insertStock(1);
        productService.delete(productId);
        assertEquals(0, integer("SELECT COUNT(*) FROM product WHERE id = ?", productId));
        assertEquals(0, integer("SELECT COUNT(*) FROM stock WHERE product_id = ?", productId));
    }

    @Test
    void deletingProductWithHistoryRollsBackStockRemoval() {
        insertStock(1);
        UUID orderId = createOrder();
        assertThrows(com.e.commerce.exception.DatabaseException.class, () -> productService.delete(productId));
        assertEquals(1, integer("SELECT COUNT(*) FROM product WHERE id = ?", productId));
        assertEquals(1, integer("SELECT reserved_quantity FROM stock WHERE product_id = ?", productId));
        assertEquals(1, integer("SELECT COUNT(*) FROM order_item WHERE order_id = ?", orderId));
        assertEquals(1, integer("SELECT COUNT(*) FROM payment WHERE order_id = ?", orderId));
    }

    @Test
    void catalogLimitsPersistWithoutTruncationOrRounding() {
        jdbcTemplate.update("INSERT INTO category(id, name) VALUES (?, ?)", UUID.randomUUID(), "Test category");
        BigDecimal maxPrice = new BigDecimal("9".repeat(36) + ".99");
        var request = new com.e.commerce.dto.request.ProductRequest("Boundary product", "D".repeat(500),
                maxPrice, "https://example.com/" + "i".repeat(480), new String[]{"Test category"});
        var product = productService.create(request);
        assertEquals(500, text("SELECT description FROM product WHERE id = ?", product.getId()).length());
        assertEquals(500, text("SELECT image_url FROM product WHERE id = ?", product.getId()).length());
        assertEquals(maxPrice, jdbcTemplate.queryForObject("SELECT price FROM product WHERE id = ?", BigDecimal.class, product.getId()));
    }

    @Test
    void migrationsUpgradeFromV1ToV6AndPreserveExistingData() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var initial = org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).target("1").load();
        initial.migrate();
        jdbcTemplate.update("INSERT INTO " + schema + ".product(id,name,description,price,image_url) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), "Legacy", "Legacy description", BigDecimal.TEN, "legacy.png");
        var upgraded = org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema).defaultSchema(schema).load();
        assertEquals(5, upgraded.migrate().migrationsExecuted);
        upgraded.validate();
        assertEquals("6", upgraded.info().current().getVersion().getVersion());
        assertEquals(1, integer("SELECT COUNT(*) FROM " + schema + ".product"));
        assertEquals(1, integer("SELECT COUNT(*) FROM " + schema + ".stock"));
        assertEquals(0, integer("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'stock' AND column_name = 'version'", schema));
    }

    private java.net.http.HttpResponse<String> http(String method, String path, String body, boolean authenticated) throws Exception {
        var builder = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:" + port + path))
                .timeout(java.time.Duration.ofSeconds(30)).header("Content-Type", "application/json");
        if (authenticated) {
            var user = new com.e.commerce.entity.User();
            user.setId(userId); user.setEmail(userId + "@example.com"); user.setRole(com.e.commerce.enums.Role.USER);
            builder.header("Authorization", "Bearer " + jwtService.generateToken(user));
        }
        builder.method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody() : java.net.http.HttpRequest.BodyPublishers.ofString(body));
        return java.net.http.HttpClient.newHttpClient().send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
    }

    private UUID createOrder() {
        OrderResponse response = orderService.create(orderRequest(), userId);
        return response.getId();
    }

    private OrderRequest orderRequest() {
        return new OrderRequest(List.of(new OrderItemRequest(productId, 1)));
    }

    private void insertStock(int totalQuantity) {
        jdbcTemplate.update(
                "INSERT INTO stock (product_id, total_quantity, reserved_quantity) VALUES (?, ?, 0)",
                productId, totalQuantity
        );
    }

    private int integer(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Integer.class, args);
    }

    private String text(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrompida", exception);
        }
    }
}
