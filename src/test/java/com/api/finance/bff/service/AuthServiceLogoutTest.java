package com.api.finance.bff.service;

import com.api.finance.bff.event.BffSessionRevokedEvent;
import com.api.finance.bff.model.AuthSession;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.bff.repository.OAuthLoginAttemptStore;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.subscription.application.port.in.SubscriptionAccountDeletionPort;
import com.api.finance.user.application.port.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceLogoutTest {

    private static final String SESSION_ID = "test-session-id";
    private static final String KEYCLOAK_ID = "test-user-id";
    private static final String INDEX_KEY = "auth:user-sessions:" + KEYCLOAK_ID;
    private static final String LOCK_KEY =
            "auth:identity-lifecycle-lock:" + KEYCLOAK_ID;

    private AuthSessionRepository sessionRepository;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;
    private ApplicationEventPublisher events;
    private RestClient restClient;
    private AuthService authService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        sessionRepository = mock(AuthSessionRepository.class);
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        events = mock(ApplicationEventPublisher.class);
        restClient = mock(RestClient.class);

        when(redis.opsForValue()).thenReturn(valueOperations);
        when(redis.opsForSet()).thenReturn(setOperations);

        when(valueOperations.setIfAbsent(
                anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        when(redis.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString()))
                .thenReturn(1L);

        authService = new AuthService(
                restClient,
                sessionRepository,
                redis,
                mock(OAuthLoginAttemptStore.class),
                mock(UserDirectoryPort.class),
                mock(SubscriptionAccountDeletionPort.class),
                mock(JwtDecoder.class),
                mock(AuthenticatedUserProvider.class),
                events,
                mock(KeycloakAccountDeletionProcessor.class)
        );
    }

    @Test
    void deveRemoverSessaoPublicarEventoEAtualizarIndice() {
        AuthSession session = session();

        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session), Optional.of(session));

        // Simula indisponibilidade do Keycloak durante a revogação remota.
        when(restClient.post())
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertDoesNotThrow(() -> authService.logout(SESSION_ID));

        verify(sessionRepository).delete(session);
        verify(events).publishEvent(argThat((Object event) ->
                event instanceof BffSessionRevokedEvent revoked
                        && SESSION_ID.equals(revoked.sessionId())));

        verify(setOperations).remove(INDEX_KEY, SESSION_ID);
        verify(valueOperations).setIfAbsent(
                eq(LOCK_KEY), anyString(), any(Duration.class));
        verify(redis).execute(
                any(DefaultRedisScript.class),
                eq(List.of(LOCK_KEY)),
                anyString());
    }

    @Test
    void naoDeveFalharQuandoSessaoJaNaoExiste() {
        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.logout(SESSION_ID));

        verify(sessionRepository, never()).delete(any(AuthSession.class));
        verifyNoInteractions(redis);
        verifyNoInteractions(events);
        verifyNoInteractions(restClient);
    }

    @Test
    void naoDeveRemoverNovamenteQuandoSessaoDesapareceDuranteLock() {
        AuthSession session = session();

        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session), Optional.empty());

        assertDoesNotThrow(() -> authService.logout(SESSION_ID));

        verify(sessionRepository, never()).delete(any(AuthSession.class));
        verifyNoInteractions(events);
        verify(redis).execute(
                any(DefaultRedisScript.class),
                eq(List.of(LOCK_KEY)),
                anyString());
    }

    @Test
    void falhaAoAtualizarIndiceNaoDeveImpedirRevogacaoLocal() {
        AuthSession session = session();

        when(sessionRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(session), Optional.of(session));

        when(setOperations.remove(INDEX_KEY, SESSION_ID))
                .thenThrow(new IllegalStateException("Redis index unavailable"));

        when(restClient.post())
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertDoesNotThrow(() -> authService.logout(SESSION_ID));

        verify(sessionRepository).delete(session);
        verify(events).publishEvent(any(BffSessionRevokedEvent.class));
        verify(setOperations).remove(INDEX_KEY, SESSION_ID);
    }

    private AuthSession session() {
        return AuthSession.builder()
                .sessionId(SESSION_ID)
                .keycloakId(KEYCLOAK_ID)
                .accessToken("test-access-token")
                .refreshToken("test-refresh-token")
                .build();
    }
}
