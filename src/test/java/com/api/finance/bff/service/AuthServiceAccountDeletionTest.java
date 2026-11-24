package com.api.finance.bff.service;

import com.api.finance.bff.event.BffSessionRevokedEvent;
import com.api.finance.bff.model.AuthSession;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.bff.repository.OAuthLoginAttemptStore;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.user.application.port.UserDirectoryPort;
import com.api.finance.subscription.application.port.in.SubscriptionAccountDeletionPort;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceAccountDeletionTest {

    @SuppressWarnings("unchecked")
    @Test
    void revokesAllKnownSessionsAndDeletesProviderAndLocalIdentity() {
        UUID keycloakId = UUID.randomUUID();
        AuthSession first = session("session-1", keycloakId.toString());
        AuthSession second = session("session-2", keycloakId.toString());
        AuthSession legacy = session("session-legacy", keycloakId.toString());

        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        UserDirectoryPort users = mock(UserDirectoryPort.class);
        AuthenticatedUserProvider userProvider = mock(AuthenticatedUserProvider.class);
        var events = mock(org.springframework.context.ApplicationEventPublisher.class);
        KeycloakAccountDeletionProcessor deletionProcessor = mock(KeycloakAccountDeletionProcessor.class);
        SubscriptionAccountDeletionPort subscriptionDeletion = mock(SubscriptionAccountDeletionPort.class);
        UUID internalUserId = UUID.randomUUID();

        when(sessions.findAllByKeycloakId(keycloakId.toString())).thenReturn(List.of(first, second));
        when(sessions.findAll()).thenReturn(List.of(first, second, legacy));
        when(sessions.findById("session-1")).thenReturn(Optional.of(first));
        when(sessions.findById("session-2")).thenReturn(Optional.of(second));
        when(sessions.findById("session-legacy")).thenReturn(Optional.of(legacy));
        when(redis.opsForSet()).thenReturn(setOperations);
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(any(String.class), any(String.class), any(java.time.Duration.class)))
                .thenReturn(true);
        when(setOperations.members("auth:user-sessions:" + keycloakId))
                .thenReturn(Set.of("session-1", "session-2"));
        when(userProvider.get(any(Jwt.class))).thenReturn(new AuthenticatedUser(keycloakId));
        when(users.findInternalUserId(keycloakId)).thenReturn(Optional.of(internalUserId));

        AuthService service = new AuthService(
                mock(org.springframework.web.client.RestClient.class),
                sessions,
                redis,
                mock(OAuthLoginAttemptStore.class),
                users,
                subscriptionDeletion,
                mock(org.springframework.security.oauth2.jwt.JwtDecoder.class),
                userProvider,
                events,
                deletionProcessor
        );

        Jwt jwt = Jwt.withTokenValue("opaque")
                .header("alg", "RS256")
                .subject(keycloakId.toString())
                .build();
        service.processarExclusaoConta(jwt, "session-1", mock(HttpServletResponse.class));

        verify(sessions).delete(first);
        verify(sessions).delete(second);
        verify(sessions).delete(legacy);
        verify(subscriptionDeletion).cancelarParaExclusao(internalUserId);
        verify(users).deleteByIdentityProviderId(keycloakId);
        verify(deletionProcessor).processOne(keycloakId);
        verify(events, org.mockito.Mockito.times(3)).publishEvent(any(BffSessionRevokedEvent.class));
        verify(redis).delete("auth:user-sessions:" + keycloakId);
    }

    private AuthSession session(String id, String keycloakId) {
        return AuthSession.builder()
                .sessionId(id)
                .keycloakId(keycloakId)
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .build();
    }
}
