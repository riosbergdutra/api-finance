package com.api.finance.bff.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Stores one-time OAuth login attempts in Redis.
 * The browser receives only the opaque flow id in an HttpOnly cookie.
 */
@Repository
@RequiredArgsConstructor
public class OAuthLoginAttemptStore {

    private static final String KEY_PREFIX = "oauth:login:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public LoginAttempt create(String flowId) {
        String state = randomToken(32);
        String verifier = randomToken(64);
        String challenge = sha256Url(verifier);

        redis.opsForValue().set(KEY_PREFIX + flowId, state + ":" + verifier, TTL);
        return new LoginAttempt(state, verifier, challenge);
    }

    public String consumeVerifier(String flowId, String receivedState) {
        if (flowId == null || flowId.isBlank() || receivedState == null || receivedState.isBlank()) {
            throw new InvalidOAuthLoginAttemptException();
        }

        String stored = redis.opsForValue().getAndDelete(KEY_PREFIX + flowId);
        if (stored == null) {
            throw new InvalidOAuthLoginAttemptException();
        }

        int separator = stored.indexOf(':');
        if (separator < 1) {
            throw new InvalidOAuthLoginAttemptException();
        }

        String expectedState = stored.substring(0, separator);
        String verifier = stored.substring(separator + 1);
        if (!MessageDigest.isEqual(
                expectedState.getBytes(StandardCharsets.UTF_8),
                receivedState.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidOAuthLoginAttemptException();
        }
        return verifier;
    }

    private static String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String sha256Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record LoginAttempt(String state, String verifier, String challenge) {}

    public static class InvalidOAuthLoginAttemptException extends RuntimeException {
        public InvalidOAuthLoginAttemptException() {
            super("Tentativa de autenticação inválida ou expirada.");
        }
    }
}
