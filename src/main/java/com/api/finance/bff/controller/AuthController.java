package com.api.finance.bff.controller;

import com.api.finance.bff.dto.AuthResponseDTO;
import com.api.finance.bff.service.AuthService;
import com.api.finance.bff.repository.OAuthLoginAttemptStore;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${app.cookies.secure:true}")
    private boolean secureCookies;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/session")
    public ResponseEntity<Map<String, Boolean>> session() {
        return ResponseEntity.ok(Map.of("authenticated", true));
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/login")
    public ResponseEntity<Void> login(HttpServletResponse response) {
        String flowId = UUID.randomUUID().toString();
        OAuthLoginAttemptStore.LoginAttempt attempt = authService.iniciarLogin(flowId);
        adicionarFlowCookie(response, flowId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authService.gerarUrlLogin(attempt))
                .build();
    }

    // O callback é concluído no BFF; o navegador nunca recebe authorization code ou tokens.
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state,
            @CookieValue(name = "OAUTH_FLOW", required = false) String flowId,
            HttpServletResponse response) {
        AuthResponseDTO authResponse = authService.login(code, state, flowId);
        adicionarSessionCookie(response, authResponse.sessionId());
        limparFlowCookie(response);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/auth/callback"))
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {
        if (sessionId != null) {
            authService.logout(sessionId);
        }
        limparSessionCookie(response);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {
        authService.processarExclusaoConta(jwt, sessionId, response);
        limparSessionCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {
        if (sessionId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            AuthResponseDTO authResponse = authService.refresh(sessionId);
            adicionarSessionCookie(response, authResponse.sessionId());
            return ResponseEntity.ok(authResponse);
        } catch (RuntimeException e) {
            limparSessionCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    private void adicionarFlowCookie(HttpServletResponse response, String flowId) {
        ResponseCookie cookie = ResponseCookie.from("OAUTH_FLOW", flowId)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth/callback")
                .maxAge(Duration.ofMinutes(5))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void limparFlowCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("OAUTH_FLOW", "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/auth/callback")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void adicionarSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", sessionId)
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void limparSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", "")
                .httpOnly(true)
                .secure(secureCookies)
                .path("/")
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
