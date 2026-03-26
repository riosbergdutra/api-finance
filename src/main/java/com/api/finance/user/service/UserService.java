package com.api.finance.user.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.api.finance.user.dto.LoginResponseDTO;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;
    private final RestClient restClient;

    // Lidos do application.yaml — mantenha lá, não hardcode aqui
    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    // -------------------------------------------------------------------------
    // Busca / utilitários
    // -------------------------------------------------------------------------

    /**
     * Busca um usuário pelo Keycloak ID.
     */
    public User buscarPorKeycloakId(UUID keycloakId) {
        return repository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
    }

    /**
     * Obtém o Keycloak ID do usuário a partir do JWT atual.
     */
    public UUID obterKeycloakIdAtual(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sub"));
    }

    /**
     * Obtém o ID interno do usuário sem precisar passá-lo na URL.
     */
    public UUID obterUsuarioId(Jwt jwt) {
        UUID keycloakId = obterKeycloakIdAtual(jwt);
        return buscarPorKeycloakId(keycloakId).getId();
    }

    // -------------------------------------------------------------------------
    // Criação / atualização
    // -------------------------------------------------------------------------

    /**
     * Cria ou atualiza o usuário a partir dos claims do OAuth2 login.
     */
    @Transactional
    public User criarOuAtualizarUsuario(OAuth2AuthenticationToken authToken) {
        String sub = authToken.getPrincipal().getAttribute("sub");
        String nome = authToken.getPrincipal().getAttribute("preferred_username");
        String email = authToken.getPrincipal().getAttribute("email");

        UUID keycloakId = UUID.fromString(sub);

        User usuario = repository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    User novo = new User();
                    novo.setKeycloakId(keycloakId);
                    novo.setEmail(email);
                    return novo;
                });

        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setAtivo(true);

        return repository.save(usuario);
    }

    // -------------------------------------------------------------------------
    // Token refresh
    // -------------------------------------------------------------------------

    /**
     * Usa o refresh_token para obter um novo access_token no Keycloak.
     * Atualiza o cookie refresh_token com o novo valor recebido.
     *
     * @param refreshToken valor atual do cookie refresh_token
     * @param response     usado para atualizar o cookie com o novo refresh_token
     * @return DTO com o novo access_token para o frontend
     */
    public LoginResponseDTO renovarToken(String refreshToken, HttpServletResponse response) {
        String tokenEndpoint = issuerUri + "/protocol/openid-connect/token";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        KeycloakTokenResponse keycloakResponse = restClient.post()
                .uri(tokenEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(KeycloakTokenResponse.class);

        if (keycloakResponse == null || keycloakResponse.accessToken() == null) {
            throw new RuntimeException("Resposta inválida do Keycloak");
        }

        // Atualiza o cookie com o novo refresh_token (rotação de token)
        if (keycloakResponse.refreshToken() != null) {
            ResponseCookie novoCookie = ResponseCookie.from("refresh_token", keycloakResponse.refreshToken())
                    .httpOnly(true)
                    .secure(false) // true em produção
                    .path("/")
                    .maxAge(java.time.Duration.ofDays(1))
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, novoCookie.toString());
        }

        return new LoginResponseDTO(keycloakResponse.accessToken());
    }

    // -------------------------------------------------------------------------
    // Logout / revogação
    // -------------------------------------------------------------------------

    /**
     * Revoga o refresh_token no Keycloak (backchannel logout).
     * Após isso o token fica inválido mesmo que o cookie ainda exista.
     *
     * @param refreshToken valor do cookie refresh_token
     */
    public void revogarToken(String refreshToken) {
        String revokeEndpoint = issuerUri + "/protocol/openid-connect/revoke";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("token", refreshToken);
        formData.add("token_type_hint", "refresh_token");

        restClient.post()
                .uri(revokeEndpoint)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .toBodilessEntity();
    }

    // -------------------------------------------------------------------------
    // Record interno para mapear a resposta do Keycloak
    // -------------------------------------------------------------------------

    /**
     * Mapeamento da resposta JSON do endpoint /token do Keycloak.
     */
    private record KeycloakTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("refresh_token") String refreshToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") Long expiresIn
    ) {}
}