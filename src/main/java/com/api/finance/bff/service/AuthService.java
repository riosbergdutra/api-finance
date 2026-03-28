package com.api.finance.bff.service;


import com.api.finance.bff.model.AuthSession;
import  com.api.finance.bff.dto.AuthResponseDTO;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RestClient restClient;
    private final AuthSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtDecoder jwtDecoder;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    public AuthResponseDTO login(String code) {
        // 1. Busca tokens
        KeycloakTokenResponse response = buscarTokensNoKeycloak("authorization_code", "code", code);

        // 2. Extrai dados do Token
        Jwt jwt = jwtDecoder.decode(response.accessToken());
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("name");

        // 3. Persiste o usuário se não existir
        synchronized (this) {
            if (!userRepository.existsByKeycloakId(UUID.fromString(keycloakId))) {
                User novoUsuario = new User();
                novoUsuario.setKeycloakId(UUID.fromString(keycloakId));
                novoUsuario.setEmail(email);
                novoUsuario.setNome(nome);
                userRepository.save(novoUsuario);
            }
        }

        // 4. Cria sessão no Redis
        String sessionId = criarSessaoNoRedis(response.refreshToken(), keycloakId);

        return new AuthResponseDTO(response.accessToken(), sessionId);
    }

    public AuthResponseDTO refresh(String oldSessionId) {
        // Busca a sessão atual no Redis
        AuthSession oldSession = sessionRepository.findById(oldSessionId)
                .orElseThrow(() -> new RuntimeException("Sessão inválida ou expirada"));

        // Pede novo token ao Keycloak
        KeycloakTokenResponse response = buscarTokensNoKeycloak("refresh_token", "refresh_token", oldSession.getRefreshToken());

        // ROTAÇÃO: Deleta a velha e cria uma nova com NOVO ID
        sessionRepository.delete(oldSession);
        String newSessionId = criarSessaoNoRedis(response.refreshToken(), oldSession.getKeycloakId());

        return new AuthResponseDTO(response.accessToken(), newSessionId);
    }

    public void logout(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            revogarNoKeycloak(session.getRefreshToken());
            sessionRepository.delete(session);
        });
    }

    private String criarSessaoNoRedis(String refreshToken, String keycloakId) {
        String sessionId = UUID.randomUUID().toString();
        AuthSession session = AuthSession.builder()
                .sessionId(sessionId)
                .keycloakId(keycloakId)
                .refreshToken(refreshToken)
                .build();
        sessionRepository.save(session);
        return sessionId;
    }

    private KeycloakTokenResponse buscarTokensNoKeycloak(String grantType, String paramName, String paramValue) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("grant_type", grantType);
        body.add(paramName, paramValue);

        if ("authorization_code".equals(grantType)) {
            body.add("redirect_uri", "http://localhost:8080/auth/callback");
        }

        return restClient.post()
                .uri(issuerUri + "/protocol/openid-connect/token")
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
                .uri(issuerUri + "/protocol/openid-connect/logout")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private record KeycloakTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {}
}