package com.api.finance.user.controller;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api.finance.user.dto.LoginResponseDTO;
import com.api.finance.user.service.UserService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/callback")
    public ResponseEntity<LoginResponseDTO> callback(
            @RequestParam String code,
            HttpServletResponse response) {

        LoginResponseDTO tokens = userService.trocarCodePorToken(code);
        adicionarRefreshTokenNoCookie(response, tokens.getRefreshToken());
        
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        LoginResponseDTO tokens = userService.refreshToken(refreshToken);
        adicionarRefreshTokenNoCookie(response, tokens.getRefreshToken());

        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response) {
        
        if (refreshToken != null) {
            userService.fazerLogoutNoKeycloak(refreshToken);
        }
        
        limparCookieRefreshToken(response);
        return ResponseEntity.noContent().build();
    }

    // Métodos privados para limpar o Controller (Clean Code)
    private void adicionarRefreshTokenNoCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(false) // Mudar para true em produção (HTTPS)
                .path("/")
                .maxAge(Duration.ofDays(30))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void limparCookieRefreshToken(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}