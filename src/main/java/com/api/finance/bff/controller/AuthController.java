package com.api.finance.bff.controller;

import com.api.finance.bff.dto.AuthResponseDTO; // IMPORT CORRETO
import com.api.finance.bff.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/callback")
    // Trocamos LoginResponseDTO por AuthResponseDTO
    public ResponseEntity<AuthResponseDTO> callback(@RequestParam String code, HttpServletResponse response) {
        
        AuthResponseDTO authResponse = authService.login(code);
        
        // Em Records, não usamos "get", usamos o nome do campo como método: .sessionId()
        String sessionId = authResponse.sessionId(); 
        
        adicionarSessionCookie(response, sessionId);

        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDTO> refresh(
            @CookieValue(name = "SESSION_ID", required = false) String sessionId,
            HttpServletResponse response) {

        if (sessionId == null) return ResponseEntity.status(401).build();

        // Aqui pegamos o DTO completo (Token + Novo ID)
        AuthResponseDTO authResponse = authService.refresh(sessionId);

        // IMPORTANTE: Atualiza o cookie com o novo ID gerado
        adicionarSessionCookie(response, authResponse.sessionId());

        return ResponseEntity.ok(authResponse);
    }

    private void adicionarSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from("SESSION_ID", sessionId)
                .httpOnly(true)
                .secure(false) // Mudar para true em produção
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}