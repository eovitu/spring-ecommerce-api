package com.e.commerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.rabbitmq.dynamic=false", "jobs.stock-expiration.initial-delay=3600000", "jobs.outbox.initial-delay=3600000"
})
@Testcontainers
class LoginRateLimitHttpTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("security.jwt.secret-key", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        r.add("security.webhook.secret", () -> "integration-webhook-secret");
        r.add("spring.rabbitmq.password", () -> "integration-rabbit-password");
        r.add("spring.rabbitmq.port", () -> "1");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired com.e.commerce.service.AuthService auth;
    @Autowired com.e.commerce.service.LoginAttemptService attempts;
    @Autowired com.e.commerce.repository.UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired com.e.commerce.service.JwtService jwt;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.e.commerce.repository.LoginAttemptRepository repository;
    @LocalServerPort int port;
    @BeforeEach void reset() { jdbc.update("DELETE FROM login_attempt"); }
    @Test void tenthFailureBlocksAndCommitsBothCountersBeforeHttpError() throws Exception {
        for(int i=1;i<=9;i++) assertEquals(401, login("absent@example.com", "Wrong@123").statusCode());
        var blocked=login("absent@example.com", "Wrong@123");
        assertEquals(429,blocked.statusCode());
        assertEquals("900",blocked.headers().firstValue("Retry-After").orElseThrow());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=10 AND blocked_until IS NOT NULL",Integer.class));
    }

    @Test void correctPasswordCannotBypassBlockAndBlockedAttemptsDoNotExtendIt() throws Exception {
        String email=insertUser();
        for(int i=0;i<10;i++) login(email,"Wrong@123");
        var before=jdbc.queryForList("SELECT blocked_until,failures::text FROM login_attempt ORDER BY key_hash");
        assertEquals(429,login(email,"Senha@123").statusCode());
        assertEquals(before,jdbc.queryForList("SELECT blocked_until,failures::text FROM login_attempt ORDER BY key_hash"));
    }

    @Test void successfulLoginDoesNotResetFailures() throws Exception {
        String email=insertUser();
        for(int i=0;i<8;i++) assertEquals(401,login(email,"Wrong@123").statusCode());
        assertEquals(200,login(email,"Senha@123").statusCode());
        assertEquals(401,login(email,"Wrong@123").statusCode());
        assertEquals(429,login(email,"Wrong@123").statusCode());
    }

    @Test void accountCounterIsSharedBetweenDifferentIps() {
        for(int i=0;i<9;i++) invalid("shared@example.com","ip-"+i);
        assertThrows(com.e.commerce.exception.LoginRateLimitException.class,
            ()->auth.login(credentials("shared@example.com"),"another-ip"));
    }

    @Test void ipCounterIsSharedBetweenDifferentAccounts() {
        for(int i=0;i<9;i++) invalid("account"+i+"@example.com","shared-ip");
        assertThrows(com.e.commerce.exception.LoginRateLimitException.class,
            ()->auth.login(credentials("last@example.com"),"shared-ip"));
    }

    @Test void forgedForwardingHeadersCannotChangeRemoteIp() throws Exception {
        for(int i=0;i<9;i++) assertEquals(401,login("spoof"+i+"@example.com","Wrong@123", "203.0.113."+i).statusCode());
        assertEquals(429,login("spoof-last@example.com","Wrong@123","198.51.100.1").statusCode());
        assertEquals(11,jdbc.queryForObject("SELECT count(*) FROM login_attempt",Integer.class));
    }

    @Test void counterCommitsDespiteOuterRollbackAndAnotherServiceSeesIt() {
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class,()->transaction.execute(status->{
            invalid("rollback@example.com","origin");
            throw new IllegalStateException("outer rollback");
        }));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=1",Integer.class));
        var independentRepository=new com.e.commerce.repository.LoginAttemptRepository(new JdbcTemplate(jdbc.getDataSource()));
        var another=new com.e.commerce.service.LoginAttemptService(independentRepository,users,passwords,jwt);
        for(int i=0;i<8;i++) transaction.execute(status->another.authenticate(credentials("rollback@example.com"),"origin"));
        var result=transaction.execute(status->another.authenticate(credentials("rollback@example.com"),"origin"));
        assertEquals(900,result.retryAfter());
    }

    @Test void exactSlidingWindowDropsBoundaryAndRetainsNewerFailure() throws Exception {
        invalid("window@example.com","origin");
        var now=java.time.Instant.parse("2030-01-01T12:00:00Z");
        jdbc.update("UPDATE login_attempt SET failures=ARRAY[?::timestamptz, ?::timestamptz]",
            java.sql.Timestamp.from(now.minusSeconds(900)),java.sql.Timestamp.from(now.minusSeconds(899)));
        org.mockito.Mockito.doReturn(now).when(repository).now();
        invalid("window@example.com","origin");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=2",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE failures[1]=?::timestamptz",Integer.class,
            java.sql.Timestamp.from(now.minusSeconds(899))));
    }

    @Test void blockExpiresExactlyAndRetryAfterRoundsUpLargestExpiry() throws Exception {
        String email=insertUser();
        login(email,"Wrong@123");
        var now=java.time.Instant.parse("2030-01-01T12:00:00Z");
        org.mockito.Mockito.doReturn(now).when(repository).now();
        jdbc.update("UPDATE login_attempt SET blocked_until=?::timestamptz, retained_until=?::timestamptz",
            java.sql.Timestamp.from(now.plusMillis(1001)),java.sql.Timestamp.from(now.plusSeconds(2)));
        String one=jdbc.queryForObject("SELECT min(key_hash) FROM login_attempt",String.class);
        jdbc.update("UPDATE login_attempt SET blocked_until=?::timestamptz WHERE key_hash=?",java.sql.Timestamp.from(now.plusMillis(2001)),one);
        var blocked=login(email,"Senha@123");
        assertEquals(429,blocked.statusCode());
        assertEquals("3",blocked.headers().firstValue("Retry-After").orElseThrow());
        jdbc.update("UPDATE login_attempt SET blocked_until=?::timestamptz, failures=ARRAY[?::timestamptz]",
            java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.minusSeconds(900)));
        assertEquals(200,login(email,"Senha@123").statusCode());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=0 AND blocked_until IS NULL",Integer.class));
    }

    @Test void concurrentRequestsRecordExactlyTenFailuresAndNeverOverrun() throws Exception {
        var ready=new java.util.concurrent.CountDownLatch(16);
        var start=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<16;i++) futures.add(pool.submit(()->{
                ready.countDown();start.await();return login("concurrent@example.com","Wrong@123").statusCode();
            }));
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            int unauthorized=0,blocked=0;
            for(var f:futures) {int status=f.get(30,java.util.concurrent.TimeUnit.SECONDS);if(status==401) unauthorized++;else if(status==429) blocked++;else fail("HTTP "+status);}
            assertEquals(9,unauthorized);assertEquals(7,blocked);
        }
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=10",Integer.class));
    }

    @Test void cleanupSkipsLockedKeyAndDoesNotLoseFreshConcurrentFailure() throws Exception {
        invalid("cleanup@example.com","origin");
        jdbc.update("UPDATE login_attempt SET retained_until=clock_timestamp()-interval '1 second'");
        var locked=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var pending=pool.submit(()->new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->{
                jdbc.queryForList("SELECT key_hash FROM login_attempt ORDER BY key_hash FOR UPDATE");
                locked.countDown();
                try {assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));} catch(InterruptedException e) {throw new RuntimeException(e);}
                for(String key:jdbc.queryForList("SELECT key_hash FROM login_attempt ORDER BY key_hash",String.class)) {
                    repository.recordFailure(key,repository.now());
                }
                return null;
            }));
            assertTrue(locked.await(10,java.util.concurrent.TimeUnit.SECONDS));
            attempts.cleanup();
            assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt",Integer.class));
            release.countDown();pending.get(20,java.util.concurrent.TimeUnit.SECONDS);
        }
        attempts.cleanup();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=2",Integer.class));
        jdbc.update("UPDATE login_attempt SET retained_until=clock_timestamp()-interval '1 second'");
        attempts.cleanup();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM login_attempt",Integer.class));
        invalid("cleanup@example.com","origin");
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM login_attempt WHERE cardinality(failures)=1",Integer.class));
    }

    @Test void invalidDtoRemainsValidationErrorWithoutCounting() throws Exception {
        assertEquals(422,login("invalid-email","Wrong@123").statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM login_attempt",Integer.class));
    }

    @Test void persistenceFailureReturnsGeneric503() throws Exception {
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("private database detail"))
            .when(repository).lock(org.mockito.ArgumentMatchers.anyString());
        var response=login("unavailable@example.com","Wrong@123");
        assertEquals(503,response.statusCode());assertFalse(response.body().contains("private database detail"));
    }

    @Test void missingLimiterStorageFailsClosedEvenWithValidCredentials() throws Exception {
        String email = insertUser();
        jdbc.execute("ALTER TABLE login_attempt RENAME TO login_attempt_unavailable");
        try {
            var response = login(email, "Senha@123");
            assertEquals(503, response.statusCode());
            assertFalse(response.body().contains("login_attempt"));
            assertFalse(response.body().contains(email));
            assertFalse(response.body().contains("token"));
        } finally {
            jdbc.execute("ALTER TABLE login_attempt_unavailable RENAME TO login_attempt");
        }
    }

    void invalid(String email,String ip) {
        assertThrows(com.e.commerce.exception.UnauthorizedException.class,()->auth.login(credentials(email),ip));
    }
    com.e.commerce.dto.request.LoginRequest credentials(String email) {
        return new com.e.commerce.dto.request.LoginRequest(email,"Wrong@123");
    }
    String insertUser() {
        var id=java.util.UUID.randomUUID();String email=id+"@example.com";
        jdbc.update("INSERT INTO tb_user(id,name,email,password,role,created_at,updated_at) VALUES (?,?,?,?,?,now(),now())",
            id,"Cliente",email,passwords.encode("Senha@123"),"USER");return email;
    }
    HttpResponse<String> login(String email,String password) throws Exception {
        return login(email,password,null);
    }
    HttpResponse<String> login(String email,String password,String forwarded) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/auth/login"))
            .timeout(java.time.Duration.ofSeconds(30))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"email\":\""+email+"\",\"password\":\""+password+"\"}"));
        if(forwarded!=null) request.header("X-Forwarded-For",forwarded).header("Forwarded","for="+forwarded);
        return HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
