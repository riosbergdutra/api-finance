package com.api.finance.bff.service;

import com.api.finance.bff.dto.AuthResponseDTO;
import com.api.finance.bff.model.AuthSession;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.bff.repository.OAuthLoginAttemptStore;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceRefreshConcurrencyTest {

    private AuthSessionRepository sessionRepository;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private RestClient restClient;
    private AuthService authService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        sessionRepository = mock(AuthSessionRepository.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);

        when(redis.opsForValue()).thenReturn(valueOperations);

        authService = new AuthService(
                restClient,
                sessionRepository,
                redis,
                mock(OAuthLoginAttemptStore.class),
                mock(UserRepository.class),
                mock(JwtDecoder.class),
                mock(AuthenticatedUserProvider.class),
                mock(ApplicationEventPublisher.class)
        );
        ReflectionTestUtils.setField(authService, "clientId", "test-client");
        ReflectionTestUtils.setField(authService, "clientSecret", "test-secret");
        ReflectionTestUtils.setField(authService, "issuerUri", "http://keycloak.test/realms/test");
    }

    @Test
    void deveEvitarSegundoRefreshQuandoOutraRequisicaoJaRotacionouOToken() {
        String sessionId = "opaque-session-id";
        AuthSession observed = session(sessionId, "old-refresh-token");
        AuthSession rotated = session(sessionId, "new-refresh-token");

        when(sessionRepository.findById(sessionId))
                .thenReturn(Optional.of(observed), Optional.of(rotated));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redis.execute(
                any(DefaultRedisScript.class),
                any(List.class),
                anyString()
        )).thenReturn(1L);

        AuthResponseDTO result = authService.refresh(sessionId);

        assertEquals(sessionId, result.sessionId());
        verify(sessionRepository, org.mockito.Mockito.times(2)).findById(sessionId);
        verify(sessionRepository, never()).save(any(AuthSession.class));
        verify(restClient, never()).post();
        verify(redis).execute(
                any(DefaultRedisScript.class),
                eq(List.of("auth:refresh-lock:" + sessionId)),
                anyString()
        );
    }

    private AuthSession session(String sessionId, String refreshToken) {
        return AuthSession.builder()
                .sessionId(sessionId)
                .keycloakId("user-123")
                .accessToken("access-token")
                .refreshToken(refreshToken)
                .build();
    }
}
