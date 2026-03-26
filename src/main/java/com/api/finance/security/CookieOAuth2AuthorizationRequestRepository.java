package com.api.finance.security;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Base64;

import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.SerializationUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Implementação stateless do AuthorizationRequestRepository.
 *
 * O repositório padrão do Spring (HttpSessionOAuth2AuthorizationRequestRepository)
 * salva o "state" do OAuth2 na HttpSession — isso força a criação de um JSESSIONID
 * mesmo quando SessionCreationPolicy.STATELESS está configurado.
 *
 * Esta classe substitui esse comportamento salvando o state em um cookie
 * HttpOnly temporário, mantendo o fluxo completamente stateless.
 */
@Component
public class CookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_EXPIRE_SECONDS = 180; // 3 minutos — tempo suficiente para o login

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookieValue(request, COOKIE_NAME)
                .map(this::deserialize)
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authorizationRequest == null) {
            // Limpa o cookie quando o request é nulo (cancelamento do fluxo)
            deleteCookie(response, COOKIE_NAME);
            return;
        }

        String serialized = serialize(authorizationRequest);

        Cookie cookie = new Cookie(COOKIE_NAME, serialized);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
        cookie.setSecure(false); // true em produção (HTTPS)
        response.addCookie(cookie);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {

        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        if (authRequest != null) {
            deleteCookie(response, COOKIE_NAME);
        }
        return authRequest;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private java.util.Optional<String> getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return java.util.Optional.empty();

        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return java.util.Optional.of(cookie.getValue());
            }
        }
        return java.util.Optional.empty();
    }

    private void deleteCookie(HttpServletResponse response, String name) {
        Cookie cookie = new Cookie(name, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0); // instrui o browser a deletar
        response.addCookie(cookie);
    }

    private String serialize(OAuth2AuthorizationRequest object) {
        return Base64.getUrlEncoder().encodeToString(
                SerializationUtils.serialize(object));
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        byte[] data = Base64.getUrlDecoder().decode(value);
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bis)) {

            return (OAuth2AuthorizationRequest) ois.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            throw new IllegalArgumentException("Failed to deserialize OAuth2AuthorizationRequest", ex);
        }
    }
}