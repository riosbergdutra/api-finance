package com.api.finance.subscription.controller;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.config.AuthenticatedUserProvider;
import com.api.finance.subscription.dto.SubscriptionResponse;
import com.api.finance.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "Plano e limites do usuário")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final AuthenticatedUserProvider userProvider;

    @GetMapping("/me")
    @Operation(summary = "Retorna o plano atual e os limites do usuário autenticado")
    public ResponseEntity<SubscriptionResponse> getMeuPlano(@AuthenticationPrincipal Jwt jwt) {
        AuthenticatedUser caller = userProvider.get(jwt);
        return ResponseEntity.ok(subscriptionService.getMeuPlano(caller));
    }

    /**
     * Endpoint de webhook do Mercado Pago.
     * Em produção, deve validar a assinatura X-Signature do MP antes de processar.
     * Mantido simples aqui — a lógica completa de integração MP fica fora do escopo inicial.
     */
    @PostMapping("/webhook/mercadopago")
    @Operation(summary = "Webhook do Mercado Pago — ativa/cancela planos")
    public ResponseEntity<Void> webhookMercadoPago(@RequestBody String payload) {
        // TODO: validar X-Signature do Mercado Pago
        // TODO: parsear evento e chamar subscriptionService.ativarPro() ou cancelarPro()
        return ResponseEntity.ok().build();
    }
}
