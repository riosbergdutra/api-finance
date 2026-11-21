package com.api.finance.bff.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthLoginAttemptStoreTest {

    @SuppressWarnings("unchecked")
    @Test
    void consumesVerifierOnlyWhenStateMatches() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("oauth:login:flow-123")).thenReturn("expected-state:pkce-verifier");

        OAuthLoginAttemptStore store = new OAuthLoginAttemptStore(redis);

        assertEquals("pkce-verifier", store.consumeVerifier("flow-123", "expected-state"));
        verify(values).getAndDelete("oauth:login:flow-123");
    }

    @SuppressWarnings("unchecked")
    @Test
    void consumesAttemptEvenWhenStateDoesNotMatch() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.getAndDelete("oauth:login:flow-456")).thenReturn("expected-state:pkce-verifier");

        OAuthLoginAttemptStore store = new OAuthLoginAttemptStore(redis);

        assertThrows(OAuthLoginAttemptStore.InvalidOAuthLoginAttemptException.class,
                () -> store.consumeVerifier("flow-456", "wrong-state"));
        verify(values).getAndDelete("oauth:login:flow-456");
    }
}
