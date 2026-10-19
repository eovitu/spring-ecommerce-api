package com.e.commerce.service;

import com.e.commerce.entity.User;
import com.e.commerce.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.crypto.SecretKey;

/**
 * Encapsula a emissão, leitura e validação de JWT.
 *
 * <p>O token utiliza o e-mail como subject e assinatura HMAC baseada em chave
 * externa configurada via propriedades da aplicação.
 */
@Service
public class JwtService {

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    /**
     * Extrai o subject do token, que representa o identificador principal do usuário.
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extrai uma claim específica do token assinado.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Gera um JWT sem claims adicionais.
     *
     * @param email e-mail usado como subject
     * @return token assinado
     */
    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("role", user.getRole().name());
        if (user.getSessionVersion() < 0) throw new IllegalArgumentException("Sessao invalida");
        claims.put("sessionVersion", user.getSessionVersion());
        return generateToken(claims, user.getEmail());
    }

    /**
     * Gera um JWT com claims adicionais.
     */
    public String generateToken(Map<String, Object> extraClaims, String email) {
        return buildToken(extraClaims, email, jwtExpiration);
    }

    /**
     * Retorna o tempo de expiração configurado, em milissegundos.
     */
    public long getExpirationTime() {
        return jwtExpiration;
    }

    /**
     * Monta e assina o token com data de emissão e expiração.
     */
    private String buildToken(
            Map<String, Object> extraClaims,
            String email,
            long expiration
    ) {
        long now = System.currentTimeMillis();
        return Jwts
                .builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Valida correspondência entre subject e expiração.
     */
    public boolean isTokenValid(String token, String email) {
        final String username = extractUsername(token);
        return (username.equals(email)) && !isTokenExpired(token);
    }

    public UUID extractUserId(String token) {
        String value = extractClaim(token, claims -> claims.get("userId", String.class));
        if (value == null) throw new IllegalArgumentException("Sessao invalida");
        return UUID.fromString(value);
    }

    public long extractSessionVersion(String token) {
        Object value = extractClaim(token, claims -> claims.get("sessionVersion"));
        if (!(value instanceof Integer || value instanceof Long) || ((Number) value).longValue() < 0) {
            throw new IllegalArgumentException("Sessao invalida");
        }
        return ((Number) value).longValue();
    }

    public Role extractRole(String token) {
        String value = extractClaim(token, claims -> claims.get("role", String.class));
        if (value == null) throw new IllegalArgumentException("Sessao invalida");
        return Role.valueOf(value);
    }

    /**
     * Verifica se a data de expiração já foi atingida.
     */
    private boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        return expiration == null || !expiration.after(new Date());
    }

    /**
     * Extrai o instante de expiração do token.
     */
    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Faz o parse do token e valida a assinatura antes de expor as claims.
     */
    private Claims extractAllClaims(String token) {
        return Jwts
                .parser()
                .verifyWith((javax.crypto.SecretKey) getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Converte a chave configurada para o formato exigido pelo JJWT.
     */
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET deve possuir ao menos 256 bits codificados em Base64");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

