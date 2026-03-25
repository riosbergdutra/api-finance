package com.api.finance.user.controller;

import java.util.UUID;

import org.springframework.boot.autoconfigure.couchbase.CouchbaseProperties.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api.finance.user.dto.LoginResponseDTO;
import com.api.finance.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // testando endpoint protegido para obter o ID do usuário logado
    @GetMapping("/meu-id")
    public ResponseEntity<UUID> obterMeuId(@AuthenticationPrincipal Jwt jwt) {
        UUID usuarioId = userService.obterUsuarioId(jwt);
        return ResponseEntity.ok(usuarioId);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, Authentication auth) {
        return ResponseEntity.ok(new LoginResponseDTO("novo-access-token-ou-mesmo"));
    }
}