package com.api.finance.security;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.api.finance.user.dto.LoginResponseDTO;
import com.api.finance.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

        private final OAuth2AuthorizedClientService clientService;
        private final UserService userService;
        private final ObjectMapper objectMapper;

        @Override
        public void onAuthenticationSuccess(HttpServletRequest request,
                        HttpServletResponse response,
                        Authentication authentication) throws IOException {

                OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;

                OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                                authToken.getAuthorizedClientRegistrationId(),
                                authToken.getName());
                if (client == null) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Falha ao obter cliente autorizado");
                        return;
                }

                String accessToken = client.getAccessToken().getTokenValue();
                String refreshToken = (client.getRefreshToken() != null)
                                ? client.getRefreshToken().getTokenValue()
                                : null;

                // Configuração do Cookie
                if (refreshToken != null) {
                        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                                        .httpOnly(true)
                                        .secure(false) // true em prod
                                        .path("/")
                                        .maxAge(Duration.ofDays(1))
                                        .build();
                        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
                }

                // Persistência do usuário
                userService.criarOuAtualizarUsuario(authToken);

                // --- A mágica do Clean Code aqui ---
                LoginResponseDTO loginResponse = new LoginResponseDTO(
                                accessToken);

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");

                // O Jackson transforma o objeto em JSON automaticamente
                response.getWriter().write(objectMapper.writeValueAsString(loginResponse));
        }
}