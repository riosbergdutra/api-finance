package com.api.finance.user.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.api.finance.user.dto.LoginResponseDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {

    private final RestClient restClient;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.keycloak.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")
    private String issuerUri;

    public LoginResponseDTO trocarCodePorToken(String code) {
        return buscarTokensNoKeycloak("authorization_code", "code", code);
    }

    public LoginResponseDTO refreshToken(String refreshToken) {
        return buscarTokensNoKeycloak("refresh_token", "refresh_token", refreshToken);
    }

    public void fazerLogoutNoKeycloak(String refreshToken) {
        MultiValueMap<String, String> body = criarBaseFormData();
        body.add("refresh_token", refreshToken);

        enviarRequisicaoKeycloak("/protocol/openid-connect/logout", body);
    }

    // Método genérico para buscar tokens (DRY - Don't Repeat Yourself)
    private LoginResponseDTO buscarTokensNoKeycloak(String grantType, String paramName, String paramValue) {
        MultiValueMap<String, String> body = criarBaseFormData();
        body.add("grant_type", grantType);
        body.add(paramName, paramValue);

        if ("authorization_code".equals(grantType)) {
            body.add("redirect_uri", "http://localhost:8080/usuario/callback");
        }

        KeycloakTokenResponse response = enviarRequisicaoKeycloak("/protocol/openid-connect/token", body);
        
        if (response == null) throw new RuntimeException("Erro ao processar tokens no Keycloak");
        return new LoginResponseDTO(response.accessToken(), response.refreshToken());
    }

    private MultiValueMap<String, String> criarBaseFormData() {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("client_secret", clientSecret);
        return map;
    }

    private KeycloakTokenResponse enviarRequisicaoKeycloak(String path, MultiValueMap<String, String> body) {
        return restClient.post()
                .uri(issuerUri + path)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(KeycloakTokenResponse.class);
    }

    private record KeycloakTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {}
}