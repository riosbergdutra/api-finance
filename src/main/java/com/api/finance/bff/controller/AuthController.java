package com.api.finance.bff.controller;

import com.api.finance.bff.dto.AuthResponseDTO; // IMPORT CORRETO
import com.api.finance.bff.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        return ResponseEntity.status(HttpStatus.FOUND) // 302
                .header(HttpHeaders.LOCATION, authService.gerarUrlLogin())
                .build();
    }

    // Keycloak redireciona via GET por padrão
    @PostMapping("/callback")
    public ResponseEntity<AuthResponseDTO> callback(@RequestParam String code, HttpServletResponse response) {
        AuthResponseDTO authResponse = authService.login(code);

        adicionarSessionCookie(response, authResponse.sessionId());

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {

        if (sessionId != null) {
            authService.logout(sessionId);
        }

        limparSessionCookie(response);
        return ResponseEntity.noContent().build(); // 204
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {

        // O Service agora resolve tudo. O Controller só diz "VAI".
        authService.processarExclusaoConta(jwt, sessionId, response);
        limparSessionCookie(response);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {

        if (sessionId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            AuthResponseDTO authResponse = authService.refresh(sessionId);
            adicionarSessionCookie(response, authResponse.sessionId());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            // Se o Refresh Token no Keycloak expirou, limpa o cookie do usuário
            limparSessionCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private void adicionarSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", sessionId)
                .httpOnly(true)
                .secure(false) // EM PRODUÇÃO: true
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void limparSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", "")
                .maxAge(0)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}