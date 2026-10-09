package com.e.commerce.integration;

import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import com.e.commerce.service.JwtService;
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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.rabbitmq.dynamic=false", "jobs.stock-expiration.initial-delay=3600000", "jobs.outbox.initial-delay=3600000"
})
@Testcontainers
class SessionRevocationHttpTest {
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
    @Autowired JwtService jwt;
    @LocalServerPort int port;
    @Autowired com.e.commerce.service.AuthService auth;
    @Autowired com.e.commerce.service.UserService userService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test void logoutRevokesBothTokensAndRequiresAuthentication() throws Exception {
        User user = insertUser();
        String first = jwt.generateToken(user), second = token(user, 0L);
        assertNotEquals(first, second);
        assertEquals(200, http("GET", "/api/v1/users/" + user.getId(), first, null).statusCode());
        assertEquals(401, http("POST", "/auth/logout", null, null).statusCode());
        assertEquals(204, http("POST", "/auth/logout", first, null).statusCode());
        assertEquals(401, http("GET", "/api/v1/users/" + user.getId(), first, null).statusCode());
        assertEquals(401, http("GET", "/api/v1/users/" + user.getId(), second, null).statusCode());
        assertEquals(401, http("POST", "/auth/logout", first, null).statusCode());
    }
    @Test void passwordChangeRevokesBothButSamePasswordPreservesSession() throws Exception {
        User u=insertUser(); String first=jwt.generateToken(u), second=token(u,0L);
        String originalHash=jdbc.queryForObject("SELECT password FROM tb_user WHERE id=?",String.class,u.getId());
        assertEquals(200,http("PUT","/api/v1/users/"+u.getId(),first,profile(u,"Senha@123")).statusCode());
        assertEquals(originalHash,jdbc.queryForObject("SELECT password FROM tb_user WHERE id=?",String.class,u.getId()));
        assertEquals(0L,version(u));assertEquals(200,protectedStatus(u,second));
        assertEquals(200,http("PUT","/api/v1/users/"+u.getId(),first,profile(u,"NovaSenha@456")).statusCode());
        assertEquals(1L,version(u));assertEquals(401,protectedStatus(u,first));assertEquals(401,protectedStatus(u,second));
        assertEquals(401,http("POST","/auth/login",null,credentials(u,"Senha@123")).statusCode());
        assertEquals(200,http("POST","/auth/login",null,credentials(u,"NovaSenha@456")).statusCode());
        assertEquals(200,protectedStatus(u,auth.login(new com.e.commerce.dto.request.LoginRequest(u.getEmail(),"NovaSenha@456")).getToken()));
    }
    @Test void logoutAllowsFreshLoginWithCurrentVersion() throws Exception {
        User u=insertUser();String old=jwt.generateToken(u);
        assertEquals(204,http("POST","/auth/logout",old,null).statusCode());
        String fresh=auth.login(new com.e.commerce.dto.request.LoginRequest(u.getEmail(),"Senha@123")).getToken();
        assertEquals(1L,jwt.extractSessionVersion(fresh));assertEquals(200,protectedStatus(u,fresh));
    }
    @Test void deletionInvalidatesBothTokensAndForeignKeyRollbackPreservesSession() throws Exception {
        User admin=insertUser();jdbc.update("UPDATE tb_user SET role='ADMIN' WHERE id=?",admin.getId());admin.setRole(Role.ADMIN);
        String adminToken=jwt.generateToken(admin);
        User removable=insertUser();String first=jwt.generateToken(removable),second=token(removable,0L);
        assertEquals(204,http("DELETE","/api/v1/users/"+removable.getId(),adminToken,null).statusCode());
        assertEquals(401,protectedStatus(removable,first));assertEquals(401,protectedStatus(removable,second));
        User linked=insertUser();String linkedToken=jwt.generateToken(linked);
        jdbc.update("INSERT INTO tb_orders(id,moment,status,user_id,created_at,updated_at) VALUES (?,now(),'CRIADO',?,now(),now())",UUID.randomUUID(),linked.getId());
        assertEquals(409,http("DELETE","/api/v1/users/"+linked.getId(),adminToken,null).statusCode());
        assertEquals(0L,version(linked));assertEquals(200,protectedStatus(linked,linkedToken));
    }
    @Test void failedUpdateRollsBackPasswordAndVersion() throws Exception {
        User u=insertUser();String old=jwt.generateToken(u);
        assertThrows(IllegalStateException.class,()->new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status->{
            userService.update(u.getId(),new com.e.commerce.dto.request.UserRequest(u.getName(),u.getEmail(),"NovaSenha@456",null));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0L,version(u));assertEquals(200,protectedStatus(u,old));
        assertNotNull(auth.login(new com.e.commerce.dto.request.LoginRequest(u.getEmail(),"Senha@123")));
    }
    @Test void rejectedEmailUpdatePreservesPasswordAndSessions() throws Exception {
        User u=insertUser(),other=insertUser();String old=jwt.generateToken(u);
        String conflicting=profile(u,"NovaSenha@456").replace(u.getEmail(),other.getEmail());
        assertEquals(409,http("PUT","/api/v1/users/"+u.getId(),old,conflicting).statusCode());
        assertEquals(0L,version(u));assertEquals(200,protectedStatus(u,old));
        assertEquals(200,http("POST","/auth/login",null,credentials(u,"Senha@123")).statusCode());
    }
    @Test void roleAndSubjectDivergenceInvalidateTokenEvenWithoutVersionChange() throws Exception {
        User u=insertUser();String old=jwt.generateToken(u);
        jdbc.update("UPDATE tb_user SET role='ADMIN' WHERE id=?",u.getId());assertEquals(401,protectedStatus(u,old));
        jdbc.update("UPDATE tb_user SET role='USER', email=? WHERE id=?",UUID.randomUUID()+"@example.com",u.getId());
        assertEquals(401,protectedStatus(u,old));
        jdbc.update("UPDATE tb_user SET email=?, role='ADMIN', session_version=session_version+1 WHERE id=?",u.getEmail(),u.getId());
        assertEquals(401,protectedStatus(u,old));
    }
    @Test void malformedClaimsAndWrongVersionAre401() throws Exception {
        User u=insertUser();
        for(Object value:java.util.List.of("0",-1,0.5,true,new java.math.BigInteger("9223372036854775808"),1L)) {
            assertEquals(401,protectedStatus(u,token(u,value)),"Malformed claim accepted: "+value);
        }
        var claims=new java.util.HashMap<String,Object>();claims.put("userId",u.getId().toString());claims.put("role","USER");
        assertEquals(401,protectedStatus(u,jwt.generateToken(claims,u.getEmail())));
        claims.put("sessionVersion",0L);claims.remove("userId");
        assertEquals(401,protectedStatus(u,jwt.generateToken(claims,u.getEmail())));
        claims.put("userId",u.getId().toString());claims.remove("role");
        assertEquals(401,protectedStatus(u,jwt.generateToken(claims,u.getEmail())));
        var key=io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));
        String expired=io.jsonwebtoken.Jwts.builder().subject(u.getEmail()).claim("userId",u.getId().toString()).claim("role","USER")
            .claim("sessionVersion",0L).expiration(new java.util.Date(System.currentTimeMillis()-60_000)).signWith(key).compact();
        assertEquals(401,protectedStatus(u,expired));
        String noExpiry=io.jsonwebtoken.Jwts.builder().subject(u.getEmail()).claim("userId",u.getId().toString()).claim("role","USER")
            .claim("sessionVersion",0L).signWith(key).compact();
        assertEquals(401,protectedStatus(u,noExpiry));
        assertEquals(401,protectedStatus(u,"invalid"));
        assertEquals(401,protectedStatus(u,jwt.generateToken(claims,u.getEmail())+"x"));
    }
    @Test void databaseFailureIsGeneric503ForTokenAndLogin() throws Exception {
        User u=insertUser();String token=jwt.generateToken(u);
        jdbc.execute("ALTER TABLE tb_user RENAME TO unavailable_test_user");
        try {
            var response=http("GET","/api/v1/users/"+u.getId(),token,null);
            assertEquals(503,response.statusCode());assertFalse(response.body().contains(u.getEmail()));assertFalse(response.body().contains("tb_user"));
            assertEquals(503,http("POST","/auth/login",null,credentials(u,"Senha@123")).statusCode());
        } finally { jdbc.execute("ALTER TABLE unavailable_test_user RENAME TO tb_user"); }
    }
    @Test void concurrentLogoutPasswordUpdatesAndLoginsNeverLoseIncrement() throws Exception {
        User u=insertUser();String before=jwt.generateToken(u);int attempts=8;
        var ready=new java.util.concurrent.CountDownLatch(attempts);
        var start=new java.util.concurrent.CountDownLatch(1);
        var tokens=new java.util.concurrent.CopyOnWriteArrayList<String>();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(attempts)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<attempts;i++) { final int n=i;futures.add(pool.submit(()->{
                ready.countDown();assertTrue(start.await(10,java.util.concurrent.TimeUnit.SECONDS));
                if(n%2==0) auth.logout(u.getId());
                else if(n==1) userService.update(u.getId(),new com.e.commerce.dto.request.UserRequest(u.getName(),u.getEmail(),"NovaSenha@456",null));
                else {
                    try {tokens.add(auth.login(new com.e.commerce.dto.request.LoginRequest(u.getEmail(),"Senha@123")).getToken());}
                    catch(com.e.commerce.exception.UnauthorizedException expected) { }
                }
                return null;
            })); }
            assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
            for(var future:futures) future.get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(5L,version(u));assertEquals(401,protectedStatus(u,before));
        for(String emitted:tokens) assertEquals(401,protectedStatus(u,emitted));
        String after=auth.login(new com.e.commerce.dto.request.LoginRequest(u.getEmail(),"NovaSenha@456")).getToken();
        assertEquals(5L,jwt.extractSessionVersion(after));assertEquals(200,protectedStatus(u,after));
    }
    @Test void overflowingVersionFailsClosedWithoutChangingPersistedState() throws Exception {
        User u=insertUser();jdbc.update("UPDATE tb_user SET session_version=? WHERE id=?",Long.MAX_VALUE,u.getId());
        assertThrows(ArithmeticException.class,()->auth.logout(u.getId()));assertEquals(Long.MAX_VALUE,version(u));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("UPDATE tb_user SET session_version=-1 WHERE id=?",u.getId()));
    }
    @Test void migrationFromPopulatedV5BackfillsSessionVersion() {
        String schema="sessions_"+UUID.randomUUID().toString().replace("-","");
        var v5=org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).target("5").load();
        v5.migrate();UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO "+schema+".tb_user(id,name,email,password,role,created_at,updated_at) VALUES (?,?,?,?,?,now(),now())",id,"Legacy","legacy@example.com","hash","USER");
        var v6=org.flywaydb.core.Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).schemas(schema).defaultSchema(schema).load();
        assertEquals(1,v6.migrate().migrationsExecuted);v6.validate();assertEquals("6",v6.info().current().getVersion().getVersion());
        assertEquals(0L,jdbc.queryForObject("SELECT session_version FROM "+schema+".tb_user WHERE id=?",Long.class,id));
    }
    long version(User u) {return jdbc.queryForObject("SELECT session_version FROM tb_user WHERE id=?",Long.class,u.getId());}
    int protectedStatus(User u,String token) throws Exception {return http("GET","/api/v1/users/"+u.getId(),token,null).statusCode();}
    String token(User u,Object version) {
        return jwt.generateToken(java.util.Map.of("userId",u.getId().toString(),"role",u.getRole().name(),"sessionVersion",version,"jti",UUID.randomUUID().toString()),u.getEmail());
    }
    String credentials(User u,String password) {return "{\"email\":\""+u.getEmail()+"\",\"password\":\""+password+"\"}";}
    String profile(User u,String password) {return "{\"name\":\"Cliente Teste\",\"email\":\""+u.getEmail()+"\",\"password\":\""+password+"\"}";}
    User insertUser() {
        User u = new User(); u.setId(UUID.randomUUID()); u.setName("Cliente Teste");
        u.setEmail(u.getId() + "@example.com"); u.setRole(Role.USER);
        jdbc.update("INSERT INTO tb_user(id,name,email,password,role,created_at,updated_at) VALUES (?,?,?,?,?,now(),now())",
            u.getId(),u.getName(),u.getEmail(),new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("Senha@123"),"USER");
        return u;
    }
    HttpResponse<String> http(String method, String path, String token, String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(java.time.Duration.ofSeconds(30))
            .header("Content-Type","application/json");
        if(token!=null) b.header("Authorization","Bearer "+token);
        return HttpClient.newHttpClient().send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
