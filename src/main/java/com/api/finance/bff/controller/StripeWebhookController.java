package com.api.finance.bff.controller;

import com.api.finance.subscription.service.StripeWebhookService;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recebe notificações do Stripe (webhooks).
 *
 * FLUXO:
 * 1. Usuário assina o plano PRO no frontend (via Stripe Checkout)
 * 2. Stripe chama este endpoint com o evento de pagamento/assinatura
 * 3. Validamos a assinatura HMAC-SHA256 (signature header) para garantir autenticidade
 * 4. Delegamos ao StripeWebhookService conforme o tipo de evento
 *
 * SEGURANÇA:
 * - Validação obrigatória de assinatura HMAC-SHA256 (rejeita qualquer request sem assinatura válida)
 * - Endpoint público (sem JWT) — autenticação é feita pela assinatura Stripe
 * - Sempre responde 200 rapidamente; processamento pesado vai para @Async
 *
 * CONFIGURAÇÃO NECESSÁRIA (application.yaml):
 *   stripe:
 *     api-key: <sua-secret-key-stripe>
 *     webhook-secret: <seu-webhook-endpoint-secret>
 *
 * COMO OBTER AS CHAVES:
 * 1. Acesse: https://dashboard.stripe.com/apikeys
 * 2. Copie a "Secret Key" (com prefixo sk_test_ ou sk_live_)
 * 3. Configure um webhook em: https://dashboard.stripe.com/webhooks
 * 4. Selecione os eventos: payment_intent.succeeded, customer.subscription.updated, customer.subscription.deleted
 * 5. Copie o "Signing Secret" (com prefixo whsec_)
 */
@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    /**
     * Endpoint principal de recebimento de webhooks.
     *
     * O Stripe envia o payload como string no corpo da requisição
     * e um header Stripe-Signature no formato:
     *   t=<timestamp>,v1=<hmac-sha256-hex>
     *
     * O webhook service valida usando a biblioteca Stripe oficialmente.
     */
    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "stripe-signature", required = false) String sigHeader,
            @RequestBody String payload) {

        log.info("[Stripe Webhook] Recebido payload de {} bytes", payload.length());

        // Valida e desserializa o payload
        Event event = null;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.warn("[Stripe Webhook] Assinatura inválida ou erro ao desserializar: {}", e.getMessage());
            return ResponseEntity.badRequest().body("{\"error\": \"Invalid signature\"}");
        }

        log.info("[Stripe Webhook] Evento validado: type={} id={}", event.getType(), event.getId());

        // Processa o evento de forma assíncrona
        webhookService.processar(event);

        return ResponseEntity.ok("{\"status\": \"received\"}");
    }
}
