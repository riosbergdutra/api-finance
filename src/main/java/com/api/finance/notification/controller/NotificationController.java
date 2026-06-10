package com.api.finance.notification.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.notification.dto.NotificationResponse;
import com.api.finance.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Notificações do usuário")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping
    @Operation(summary = "Lista notificações paginadas (não lidas primeiro)")
    public ResponseEntity<Page<NotificationResponse>> listar(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {

        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(notificationService.listar(pageable, caller));
    }

    @GetMapping("/unread")
    @Operation(summary = "Lista apenas notificações não lidas")
    public ResponseEntity<List<NotificationResponse>> naoLidas(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(notificationService.listarNaoLidas(userProvider.get(jwt)));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "Retorna o total de notificações não lidas")
    public ResponseEntity<Map<String, Long>> contarNaoLidas(@AuthenticationPrincipal Jwt jwt) {
        long total = notificationService.contarNaoLidas(userProvider.get(jwt));
        return ResponseEntity.ok(Map.of("total", total));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Marca uma notificação como lida")
    public ResponseEntity<Void> marcarComoLida(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        notificationService.marcarComoLida(id, userProvider.get(jwt));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Marca todas as notificações como lidas")
    public ResponseEntity<Map<String, Integer>> marcarTodasComoLidas(@AuthenticationPrincipal Jwt jwt) {
        int total = notificationService.marcarTodasComoLidas(userProvider.get(jwt));
        return ResponseEntity.ok(Map.of("marcadas", total));
    }
}
