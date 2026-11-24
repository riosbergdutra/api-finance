package com.api.finance.bff.controller;

import com.api.finance.subscription.service.StripeWebhookService;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks/stripe")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

    private final StripeWebhookService webhookService;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            @RequestHeader(value = "stripe-signature", required = false) String sigHeader,
            @RequestBody String payload) {

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.warn("[Stripe] Webhook rejeitado: assinatura inválida ou payload malformado.");
            return ResponseEntity.badRequest().body("{\"error\":\"invalid_signature\"}");
        }

        // Bloqueio adicional: esta instalação aceita exclusivamente eventos de teste.
        if (Boolean.TRUE.equals(event.getLivemode())) {
            log.error("[Stripe] Evento live rejeitado. eventId={} type={}", event.getId(), event.getType());
            return ResponseEntity.badRequest().body("{\"error\":\"live_events_disabled\"}");
        }

        log.info("[Stripe DEMO] Evento de teste validado: type={} id={}", event.getType(), event.getId());
        webhookService.processar(event);
        return ResponseEntity.ok("{\"status\":\"received\",\"mode\":\"test\"}");
    }
}
