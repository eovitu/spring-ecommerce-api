package com.e.commerce.service;

import com.e.commerce.dto.request.LoginRequest;
import com.e.commerce.dto.response.LoginResponse;
import com.e.commerce.entity.User;
import com.e.commerce.repository.LoginAttemptRepository;
import com.e.commerce.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class LoginAttemptService {
    private final LoginAttemptRepository attempts;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final String dummyHash;

    public LoginAttemptService(LoginAttemptRepository attempts, UserRepository users,
            PasswordEncoder passwords, JwtService jwt) {
        this.attempts = attempts;
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** Normal return commits failures before AuthService raises an HTTP error. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result authenticate(LoginRequest request, String remoteAddress) {
        var keys = Stream.of(key("ip", remoteAddress), key("account", request.getEmail())).sorted().toList();
        keys.forEach(attempts::lock);
        Instant now = attempts.now();
        Instant blocked = keys.stream().map(attempts::blockedUntil)
                .filter(expiry -> expiry != null && expiry.isAfter(now))
                .max(Instant::compareTo).orElse(null);
        if (blocked != null) {
            return new Result(null, retryAfter(now, blocked));
        }
        keys.forEach(key -> attempts.prune(key, now));
        var user = users.findByEmailForUpdate(request.getEmail());
        boolean matches = passwords.matches(request.getPassword(), user.map(User::getPassword).orElse(dummyHash));
        if (user.isEmpty() || !matches) {
            Instant expiry = null;
            for (String key : keys) {
                Instant current = attempts.recordFailure(key, now);
                if (current != null && (expiry == null || current.isAfter(expiry))) {
                    expiry = current;
                }
            }
            return new Result(null, expiry == null ? 0 : retryAfter(now, expiry));
        }
        var response = new LoginResponse();
        response.setToken(jwt.generateToken(user.orElseThrow()));
        response.setType("Bearer");
        response.setExpiresIn(jwt.getExpirationTime() / 1000);
        return new Result(response, 0);
    }

    @Scheduled(initialDelayString = "${jobs.login-cleanup.initial-delay:60000}",
            fixedDelayString = "${jobs.login-cleanup.delay:60000}")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cleanup() {
        attempts.cleanup();
    }

    private static long retryAfter(Instant now, Instant until) {
        Duration remaining = Duration.between(now, until);
        return Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
    }

    private static String key(String domain, String identity) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("login:" + domain + ":" + identity).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    public record Result(LoginResponse response, long retryAfter) { }
}
