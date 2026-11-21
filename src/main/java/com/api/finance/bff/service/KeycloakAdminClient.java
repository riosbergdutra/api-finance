package com.api.finance.bff.service;

import com.api.finance.bff.exceptions.AuthIntegrationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

/**
 * Least-privilege Keycloak Admin REST client used only for account deletion.
 * The service-account credentials are separate from the browser-login client.
 */
@Service
@RequiredArgsConstructor
public class KeycloakAdminClient {

    private final RestClient restClient;

    @Value("${app.keycloak.admin.base-url:}")
    private String baseUrl;

    @Value("${app.keycloak.admin.realm:}")
    private String realm;

    @Value("${app.keycloak.admin.client-id:}")
    private String clientId;

    @Value("${app.keycloak.admin.client-secret:}")
    private String clientSecret;

    public void deleteUser(UUID keycloakUserId) {
        if (baseUrl.isBlank() || realm.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            throw new AuthIntegrationException(
                    "Exclusão de identidade indisponível: configure a integração administrativa do Keycloak.");
        }

        String adminToken = obtainAdminToken();
        var userUri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("admin", "realms", realm, "users", keycloakUserId.toString())
                .build().encode().toUri();

        try {
            restClient.delete()
                    .uri(userUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyDeleted) {
            // Idempotent retry: an already absent Keycloak identity is the desired state.
        }
    }

    @SuppressWarnings("unchecked")
    private String obtainAdminToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        var tokenUri = UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("realms", realm, "protocol", "openid-connect", "token")
                .build().encode().toUri();

        Map<String, Object> tokenResponse = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);

        Object token = tokenResponse == null ? null : tokenResponse.get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new AuthIntegrationException("Keycloak não retornou credencial administrativa válida.");
        }
        return value;
    }
}
