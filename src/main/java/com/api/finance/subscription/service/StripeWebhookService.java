package com.api.finance.subscription.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Processa eventos do Stripe e aciona o SubscriptionService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final SubscriptionService subscriptionService;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Async
    public void processar(Event event) {
        if (event == null || event.getType() == null) {
            log.warn("[Stripe] Evento nulo ou sem tipo");
            return;
        }

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded" -> processarPagamentoAprovado(event);
                case "customer.subscription.updated" -> processarAssinaturaAtualizada(event);
                case "customer.subscription.deleted" -> processarAssinaturaCancelada(event);
                default -> log.debug("[Stripe] Evento ignorado: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("[Stripe] Erro ao processar evento {}: {}", event.getType(), e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────
    // PAYMENT INTENT
    // ─────────────────────────────────────────────

    private void processarPagamentoAprovado(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject()
                    .orElse(null);

            if (paymentIntent == null) {
                log.warn("[Stripe] PaymentIntent nulo");
                return;
            }

            log.info("[Stripe] Pagamento aprovado id={} status={}",
                    paymentIntent.getId(), paymentIntent.getStatus());

            // ✅ CORRETO: usar metadata (não clientReferenceId)
            Map<String, String> metadata = paymentIntent.getMetadata();

            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata do paymentIntent={}",
                        paymentIntent.getId());
                return;
            }

            LocalDate inicio = LocalDate.now();
            LocalDate fim = inicio.plusDays(30);

            subscriptionService.ativarPro(
                    userId,
                    paymentIntent.getId(),
                    null,
                    inicio,
                    fim
            );

            log.info("[Stripe] PRO ativado via pagamento userId={} até={}", userId, fim);

        } catch (Exception e) {
            log.error("[Stripe] Erro em payment_intent.succeeded", e);
        }
    }

    // ─────────────────────────────────────────────
    // SUBSCRIPTION UPDATED
    // ─────────────────────────────────────────────

    private void processarAssinaturaAtualizada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject()
                            .orElse(null);

            if (stripeSub == null) {
                log.warn("[Stripe] Subscription nula");
                return;
            }

            log.info("[Stripe] Assinatura id={} status={}",
                    stripeSub.getId(), stripeSub.getStatus());

            if (!"active".equals(stripeSub.getStatus())) return;

            String customerId = stripeSub.getCustomer();

            UUID userId = resolverUserId(customerId);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado para customer={}", customerId);
                return;
            }

            Long periodEnd = stripeSub.getItems()
                    .getData()
                    .get(0)
                    .getCurrentPeriodEnd();

            if (periodEnd == null) {
                log.warn("[Stripe] currentPeriodEnd nulo subscription={}", stripeSub.getId());
                return;
            }

            LocalDate fim = Instant.ofEpochSecond(periodEnd)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            LocalDate inicio = LocalDate.now();

            subscriptionService.ativarPro(
                    userId,
                    null,
                    stripeSub.getId(),
                    inicio,
                    fim
            );

            log.info("[Stripe] PRO renovado userId={} até={}", userId, fim);

        } catch (Exception e) {
            log.error("[Stripe] Erro em subscription.updated", e);
        }
    }

    // ─────────────────────────────────────────────
    // SUBSCRIPTION DELETED
    // ─────────────────────────────────────────────

    private void processarAssinaturaCancelada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject()
                            .orElse(null);

            if (stripeSub == null) {
                log.warn("[Stripe] Subscription nula");
                return;
            }

            log.info("[Stripe] Cancelamento subscription id={}", stripeSub.getId());

            subscriptionService.expirarAssinaturasVencidas();

        } catch (Exception e) {
            log.error("[Stripe] Erro em subscription.deleted", e);
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private UUID resolverUserId(String reference) {
        if (reference == null || reference.isBlank()) return null;

        try {
            return UUID.fromString(reference);
        } catch (Exception e) {
            log.warn("[Stripe] UUID inválido: {}", reference);
            return null;
        }
    }
}