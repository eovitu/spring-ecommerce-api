package com.e.commerce.service;

import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    @Test
    void signsTokensExplicitlyWithHs256() {
        byte[] keyBytes = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        String secret = Encoders.BASE64.encode(keyBytes);
        JwtService jwtService = configuredService(secret);
        User user = user();

        String token = jwtService.generateToken(user);
        String algorithm = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(keyBytes))
                .build()
                .parseSignedClaims(token)
                .getHeader()
                .getAlgorithm();

        assertEquals("HS256", algorithm);
        assertEquals(user.getId(), jwtService.extractUserId(token));
        assertEquals(Role.USER, jwtService.extractRole(token));
    }

    @Test
    void rejectsSecretsWithLessThan256Bits() {
        JwtService jwtService = configuredService(Encoders.BASE64.encode(new byte[31]));

        assertThrows(IllegalStateException.class, () -> jwtService.generateToken(user()));
    }

    private JwtService configuredService(String secret) {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", secret);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", 3_600_000L);
        return jwtService;
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("customer@example.com");
        user.setRole(Role.USER);
        return user;
    }
}
