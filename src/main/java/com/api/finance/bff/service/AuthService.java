package com.api.finance.bff.service;


import com.api.finance.bff.event.BffSessionRevokedEvent;
import com.api.finance.bff.exceptions.AuthIntegrationException;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import com.api.finance.bff.model.AuthSession;
import com.api.finance.bff.dto.AuthResponseDTO;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.bff.repository.OAuthLoginAttemptStore;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.user.application.port.UserDirectoryPort;
import com.api.finance.subscription.application.port.in.SubscriptionAccountDeletionPort;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final RestClient restClient;
    private final AuthSessionRepository sessionRepository;
    private final StringRedisTemplate redis;
    private final OAuthLoginAttemptStore loginAttemptStore;
    private final UserDirectoryPort userDirectory;
    private final SubscriptionAccountDeletionPort subscriptionAccountDeletion;
    private final JwtDecoder jwtDecoder;
    private final AuthenticatedUserProvider userprovider;
    private final ApplicationEventPublisher eventPublisher;
    private final KeycloakAccountDeletionProcessor accountDeletionProcessor;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${app.keycloak.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri:}}")
    private String issuerUri;

    @Value("${app.keycloak.backend-uri:${app.keycloak.issuer-uri:${spring.security.oauth2.resourceserver.jwt.issuer-uri:}}}")
    private String keycloakBackendUri;

    @Value("${app.auth.redirect-uri:http://localhost:8080/auth/callback}")
    private String redirectUri;

    public AuthResponseDTO login(String code, String state, String flowId) {
        String codeVerifier = loginAttemptStore.consumeVerifier(flowId, state);
        KeycloakTokenResponse response = buscarTokensNoKeycloak(
                "authorization_code", "code", code, codeVerifier);

        Jwt jwt = jwtDecoder.decode(response.accessToken());
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("name");

        if (nome == null || nome.isBlank()) {
            nome = jwt.getClaimAsString("preferred_username");
        }

        if (nome == null || nome.isBlank()) {
            nome = email != null ? email.split("@")[0] : "Usuário";
        }

        final String emailFinal = email;
        final String nomeFinal = nome;
        return withIdentityLock(keycloakId, () -> {
            garantirExistenciaUsuario(keycloakId, emailFinal, nomeFinal);
            String sessionId = criarSessaoNoRedis(response.accessToken(), response.refreshToken(), keycloakId);
            return new AuthResponseDTO(sessionId);
        });
    }

    private void garantirExistenciaUsuario(String keycloakId, String email, String nome) {
        userDirectory.registerIfAbsent(UUID.fromString(keycloakId), email, nome);
    }

    @CircuitBreaker(name = "keycloak", fallbackMethod = "refreshFallback")
    public AuthResponseDTO refresh(String sessionId) {
        AuthSession observed = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));
        return withIdentityLock(observed.getKeycloakId(),
                () -> refreshWithinIdentityLock(sessionId));
    }

    private AuthResponseDTO refreshWithinIdentityLock(String sessionId) {
        AuthSession observed = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));
        String lockKey = "auth:refresh-lock:" + sessionId;
        String lockValue = UUID.randomUUID().toString();
        DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);

        boolean acquired = false;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        try {
            while (!(acquired = Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey, lockValue, java.time.Duration.ofSeconds(30))))) {
                if (System.nanoTime() >= deadline) {
                    throw new AuthIntegrationException("Renovação de sessão ocupada; tente novamente.");
                }
                Thread.sleep(100);
            }

            AuthSession current = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));
            if (!Objects.equals(observed.getRefreshToken(), current.getRefreshToken())) {
                return new AuthResponseDTO(sessionId);
            }

            KeycloakTokenResponse response = buscarTokensNoKeycloak(
                    "refresh_token", "refresh_token", current.getRefreshToken());
            current.setAccessToken(response.accessToken());
            current.setRefreshToken(response.refreshToken());
            sessionRepository.save(current);
            redis.expire(sessionIndexKey(current.getKeycloakId()), java.time.Duration.ofDays(30));
            return new AuthResponseDTO(sessionId);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthIntegrationException("Renovação de sessão interrompida.");
        } catch (SessionNotFoundException | AuthIntegrationException known) {
            throw known;
        } catch (RestClientResponseException providerResponse) {
            if (providerResponse.getResponseBodyAsString().contains("invalid_grant")) {
                revogarSessaoLocal(observed);
                throw new SessionNotFoundException("Sessão inválida ou expirada.");
            }
            throw new AuthIntegrationException("O provedor de autenticação não conseguiu renovar a sessão.");
        } catch (Exception failure) {
            throw new AuthIntegrationException("Não foi possível atualizar o token no Keycloak.");
        } finally {
            if (acquired) {
                try {
                    redis.execute(releaseScript, List.of(lockKey), lockValue);
                } catch (RuntimeException releaseFailure) {
                    log.warn("Não foi possível liberar imediatamente o lock de renovação; ele expirará automaticamente.");
                }
            }
        }
    }

    /**
     * Resolves the authenticated principal from the opaque browser session.
     * Access and refresh tokens never leave this service.
     */
    public Jwt autenticarSessao(String sessionId) {
        AuthSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));
        try {
            return jwtDecoder.decode(session.getAccessToken());
        } catch (JwtException expiredOrInvalid) {
            refresh(sessionId);
            AuthSession renewed = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));
            return jwtDecoder.decode(renewed.getAccessToken());
        }
    }

    private void revogarSessaoLocal(AuthSession session) {
        sessionRepository.delete(session);
        eventPublisher.publishEvent(new BffSessionRevokedEvent(session.getSessionId()));
        try {
            redis.opsForSet().remove(sessionIndexKey(session.getKeycloakId()), session.getSessionId());
        } catch (RuntimeException indexFailure) {
            log.warn("Não foi possível atualizar o índice de sessões após rejeição do refresh token.");
        }
    }

    /** Checks the authoritative Redis session record without exposing token data. */
    public boolean sessaoAtiva(String sessionId) {
        return sessionRepository.existsById(sessionId);
    }

    /**
     * Logout must always remove the local BFF session, even when Keycloak is
     * unavailable or the Resilience4j circuit breaker is open. Remote token
     * revocation is best-effort; deleting the local session is mandatory.
     */
    public void logout(String sessionId) {
        AuthSession observed = sessionRepository.findById(sessionId).orElse(null);
        if (observed == null) {
            return;
        }

        withIdentityLock(observed.getKeycloakId(), () -> {
            AuthSession session = sessionRepository.findById(sessionId).orElse(null);
            if (session == null) {
                return null;
            }

            sessionRepository.delete(session);
            eventPublisher.publishEvent(new BffSessionRevokedEvent(sessionId));
            try {
                redis.opsForSet().remove(sessionIndexKey(session.getKeycloakId()), sessionId);
            } catch (RuntimeException indexFailure) {
                log.warn("Não foi possível atualizar o índice de sessões após logout; a sessão já foi removida.");
            }

            try {
                revogarNoKeycloak(session.getRefreshToken());
            } catch (Exception e) {
                log.warn("Falha ao revogar token no Keycloak durante logout; sessão BFF já foi invalidada.");
            }
            return null;
        });
    }

    public void processarExclusaoConta(Jwt jwt, String sessionId, HttpServletResponse response) {
        AuthenticatedUser user = userprovider.get(jwt);
        UUID keycloakId = user.id();
        withIdentityLock(keycloakId.toString(), () -> {
            revogarTodasSessoesEApagarConta(keycloakId, sessionId);
            return null;
        });
    }

    private void revogarTodasSessoesEApagarConta(UUID keycloakId, String currentSessionId) {
        String identity = keycloakId.toString();
        java.util.Set<String> sessionIds = new java.util.HashSet<>();
        sessionRepository.findAllByKeycloakId(identity).forEach(s -> sessionIds.add(s.getSessionId()));
        // Redis secondary indexes are not backfilled for sessions created before
        // this release. Account deletion is rare, so scan remaining session records once.
        for (AuthSession stored : sessionRepository.findAll()) {
            if (identity.equals(stored.getKeycloakId())) {
                sessionIds.add(stored.getSessionId());
            }
        }
        java.util.Set<String> indexedIds = redis.opsForSet().members(sessionIndexKey(identity));
        if (indexedIds != null) {
            sessionIds.addAll(indexedIds);
        }
        if (currentSessionId != null && !currentSessionId.isBlank()) {
            sessionIds.add(currentSessionId);
        }

        for (String id : sessionIds) {
            sessionRepository.findById(id).ifPresent(session -> sessionRepository.delete(session));
            eventPublisher.publishEvent(new BffSessionRevokedEvent(id));
        }
        redis.delete(sessionIndexKey(identity));

        userDirectory.findInternalUserId(keycloakId)
                .ifPresent(subscriptionAccountDeletion::cancelarParaExclusao);

        // The local erasure, pseudonymous tombstone, and provider-deletion outbox
        // are committed atomically. The worker retries Keycloak after this method.
        userDirectory.deleteByIdentityProviderId(keycloakId);
        accountDeletionProcessor.processOne(keycloakId);
        log.info("Dados locais removidos e exclusão da identidade encaminhada para processamento durável.");
    }

    private String criarSessaoNoRedis(String accessToken, String refreshToken, String keycloakId) {
        String sessionId = UUID.randomUUID().toString();
        AuthSession session = AuthSession.builder()
                .sessionId(sessionId)
                .keycloakId(keycloakId)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
        sessionRepository.save(session);
        String indexKey = sessionIndexKey(keycloakId);
        redis.opsForSet().add(indexKey, sessionId);
        redis.expire(indexKey, java.time.Duration.ofDays(30));
        return sessionId;
    }

    /**
     * Serializes login/session creation, refresh, logout, and account erasure for
     * one identity. This prevents refresh or OAuth callback from recreating a
     * Redis session after account-wide revocation has already scanned sessions.
     */
    private <T> T withIdentityLock(String keycloakId, Supplier<T> operation) {
        String lockKey = "auth:identity-lifecycle-lock:" + keycloakId;
        String lockValue = UUID.randomUUID().toString();
        DefaultRedisScript<Long> releaseScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
        boolean acquired = false;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        try {
            while (!(acquired = Boolean.TRUE.equals(
                    redis.opsForValue().setIfAbsent(lockKey, lockValue, java.time.Duration.ofMinutes(10))))) {
                if (System.nanoTime() >= deadline) {
                    throw new AuthIntegrationException("Operação de sessão concorrente; tente novamente.");
                }
                Thread.sleep(100);
            }
            return operation.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AuthIntegrationException("Operação de sessão interrompida.");
        } finally {
            if (acquired) {
                try {
                    redis.execute(releaseScript, List.of(lockKey), lockValue);
                } catch (RuntimeException releaseFailure) {
                    log.warn("Não foi possível liberar o lock de ciclo de vida da sessão; ele expirará automaticamente.");
                }
            }
        }
    }

    private String sessionIndexKey(String keycloakId) {
        return "auth:user-sessions:" + keycloakId;
    }

    private KeycloakTokenResponse buscarTokensNoKeycloak(String grantType, String paramName, String paramValue) {
        return buscarTokensNoKeycloak(grantType, paramName, paramValue, null);
    }

    private KeycloakTokenResponse buscarTokensNoKeycloak(String grantType, String paramName, String paramValue, String codeVerifier) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", grantType);
        body.add(paramName, paramValue);

        if ("authorization_code".equals(grantType)) {
            body.add("redirect_uri", redirectUri);
            body.add("code_verifier", codeVerifier);
        }

        return restClient.post()
                .uri(keycloakBackendUri + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(KeycloakTokenResponse.class);
    }

    private void revogarNoKeycloak(String refreshToken) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        restClient.post()
                .uri(keycloakBackendUri + "/protocol/openid-connect/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    public OAuthLoginAttemptStore.LoginAttempt iniciarLogin(String flowId) {
        return loginAttemptStore.create(flowId);
    }

    public String gerarUrlLogin(OAuthLoginAttemptStore.LoginAttempt attempt) {
        return UriComponentsBuilder.fromUriString(issuerUri + "/protocol/openid-connect/auth")
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid profile email")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", attempt.state())
                .queryParam("code_challenge", attempt.challenge())
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode()
                .toUriString();
    }

    private record KeycloakTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    @SuppressWarnings("unused")
    private AuthResponseDTO refreshFallback(String sessionId, Throwable t) {
        if (t instanceof SessionNotFoundException invalidSession) {
            throw invalidSession;
        }
        log.warn("Circuit Breaker OPEN: Keycloak indisponível para refresh.");
        throw new AuthIntegrationException("O serviço de autenticação está temporariamente indisponível.");
    }
}
