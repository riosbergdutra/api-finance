package com.api.finance.bff.service;


import com.api.finance.bff.exceptions.AuthIntegrationException;
import com.api.finance.bff.exceptions.SessionNotFoundException;
import com.api.finance.bff.model.AuthSession;
import  com.api.finance.bff.dto.AuthResponseDTO;
import com.api.finance.bff.repository.AuthSessionRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final RestClient restClient;
    private final AuthSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final JwtDecoder jwtDecoder;
    private final AuthenticatedUserProvider userprovider;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    public AuthResponseDTO login(String code) {
        // 1. Busca tokens no Keycloak
        KeycloakTokenResponse response = buscarTokensNoKeycloak("authorization_code", "code", code);

        // 2. Extrai dados do Token (O jwtDecoder já deve estar validando a assinatura)
        Jwt jwt = jwtDecoder.decode(response.accessToken());
        String keycloakId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String nome = jwt.getClaimAsString("name");

        // 3. Persiste o usuário (Lógica "Get or Create" segura para concorrência)
        garantirExistenciaUsuario(keycloakId, email, nome);

        // 4. Cria sessão no Redis
        String sessionId = criarSessaoNoRedis(response.refreshToken(), keycloakId);

        return new AuthResponseDTO(response.accessToken(), sessionId);
    }


    private void garantirExistenciaUsuario(String keycloakId, String email, String nome) {
        UUID uuid = UUID.fromString(keycloakId);

        // 1. Otimização: Evita o INSERT se o usuário já é recorrente
        if (userRepository.existsByKeycloakId(uuid)) {
            return;
        }

        // 2. Tentativa de criação: Protegida contra condições de corrida
        try {
            User novo = new User();
            novo.setKeycloakId(uuid);
            novo.setEmail(email);
            novo.setNome(nome);
            userRepository.save(novo);
        } catch (DataIntegrityViolationException e) {
            // Se outra thread inseriu entre o 'if' e o 'save', o banco bloqueia
            log.info("Usuário {} criado simultaneamente por outra instância.", keycloakId);
        }
    }

    public AuthResponseDTO refresh(String sessionId) {
        // Trocamos RuntimeException por SessionNotFoundException
        AuthSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Sessão inválida ou expirada."));

        try {
            KeycloakTokenResponse response = buscarTokensNoKeycloak(
                    "refresh_token",
                    "refresh_token",
                    session.getRefreshToken()
            );

            session.setRefreshToken(response.refreshToken());
            sessionRepository.save(session);

            return new AuthResponseDTO(response.accessToken(), sessionId);
        } catch (Exception e) {
            // Se o Keycloak rejeitar o Refresh Token, algo deu errado na integração
            throw new AuthIntegrationException("Não foi possível atualizar o token no Keycloak.");
        }
    }

    public void logout(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            try {
                revogarNoKeycloak(session.getRefreshToken());
            } catch (Exception e) {
                log.warn("Falha ao revogar token no Keycloak para sessão {}", sessionId);
            }
            sessionRepository.delete(session);
        });
    }

    @Transactional
    public void processarExclusaoConta(Jwt jwt, String sessionId, HttpServletResponse response) {
        // 1. Usa o provider internamente para não poluir o Controller
        AuthenticatedUser user = userprovider.get(jwt);
        UUID keycloakId = user.id();

        // 2. Lógica de banco
        userRepository.deleteByKeycloakId(keycloakId);

        // 3. Lógica de Sessão/Redis
        if (sessionId != null) {
            this.logout(sessionId);
        }

        // 4. Lógica de Cookie (Opcional: você pode injetar um 'CookieComponent' aqui)
        this.limparSessionCookie(response);

        log.info("Fluxo de exclusão concluído para o usuário: {}", user.id());
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

    public String gerarUrlLogin() {
        return issuerUri + "/protocol/openid-connect/auth" +
                "?client_id=" + clientId +
                "&response_type=code" +
                "&scope=openid profile email" +
                "&redirect_uri=http://localhost:8080/auth/callback";
    }

    private record KeycloakTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    private void limparSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", "")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}