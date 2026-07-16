package com.api.finance.subscription.service;

import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
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
 *
 * FLUXO COM CHECKOUT SESSION (modo atual):
 *
 * 1. checkout.session.completed
 *    → usuário concluiu o pagamento na página do Stripe
 *    → ativa PRO imediatamente com período de 30 dias (pagamento único)
 *    → OU aguarda customer.subscription.updated para subscription recorrente
 *
 * 2. customer.subscription.updated (status=active)
 *    → subscription foi criada ou renovada
 *    → ativa/renova PRO até o fim do período (current_period_end)
 *    → este é o evento principal para mode=subscription
 *
 * 3. customer.subscription.deleted
 *    → subscription foi cancelada/expirada pelo Stripe
 *    → expira o PRO no banco
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

        log.debug("[Stripe] Processando evento: {}", event.getType());

        try {
            switch (event.getType()) {
                // Checkout Session concluída — usuário pagou
                case "checkout.session.completed"      -> processarCheckoutConcluido(event);
                // Subscription criada/renovada pelo Stripe (recorrente)
                case "customer.subscription.updated"  -> processarAssinaturaAtualizada(event);
                // Subscription cancelada/expirada
                case "customer.subscription.deleted"  -> processarAssinaturaCancelada(event);
                // Mantido para compatibilidade com pagamentos avulsos legados
                case "payment_intent.succeeded"       -> processarPagamentoAprovado(event);
                default -> log.debug("[Stripe] Evento ignorado: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("[Stripe] Erro ao processar evento {}: {}", event.getType(), e.getMessage(), e);
        }
    }

    // ── checkout.session.completed ─────────────────────────────────────────

    private void processarCheckoutConcluido(Event event) {
        try {
            Session session = (Session) event.getDataObjectDeserializer()
                    .getObject().orElse(null);

            if (session == null) {
                log.warn("[Stripe] Session nula em checkout.session.completed");
                return;
            }

            log.info("[Stripe] Checkout concluído: sessionId={} mode={}", session.getId(), session.getMode());

            Map<String, String> metadata = session.getMetadata();
            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata da session={}", session.getId());
                return;
            }

            // Para mode=subscription o Stripe vai disparar customer.subscription.updated
            // em seguida com as datas exatas do período — apenas logamos aqui.
            // Para mode=payment (pagamento único) ativamos PRO por 30 dias.
            if ("payment".equals(session.getMode())) {
                LocalDate inicio = LocalDate.now();
                LocalDate fim    = inicio.plusDays(30);
                subscriptionService.ativarPro(userId, session.getPaymentIntent(), null, inicio, fim);
                log.info("[Stripe] PRO ativado via pagamento único userId={} até={}", userId, fim);
            } else {
                // subscription → aguarda customer.subscription.updated
                log.info("[Stripe] Checkout subscription concluído userId={} — aguardando subscription.updated", userId);
            }

        } catch (Exception e) {
            log.error("[Stripe] Erro em checkout.session.completed", e);
        }
    }

    // ── customer.subscription.updated ─────────────────────────────────────

    private void processarAssinaturaAtualizada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject().orElse(null);

            if (stripeSub == null) {
                log.warn("[Stripe] Subscription nula em subscription.updated");
                return;
            }

            log.info("[Stripe] Assinatura atualizada: id={} status={}", stripeSub.getId(), stripeSub.getStatus());

            if (!"active".equals(stripeSub.getStatus())) return;

            // userId vem do metadata propagado da Checkout Session
            Map<String, String> metadata = stripeSub.getMetadata();
            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata da subscription={}", stripeSub.getId());
                return;
            }

            Long periodEnd = stripeSub.getItems()
                    .getData().get(0)
                    .getCurrentPeriodEnd();

            if (periodEnd == null) {
                log.warn("[Stripe] currentPeriodEnd nulo subscription={}", stripeSub.getId());
                return;
            }

            LocalDate fim    = Instant.ofEpochSecond(periodEnd)
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate inicio = LocalDate.now();

            subscriptionService.ativarPro(userId, null, stripeSub.getId(), inicio, fim);
            log.info("[Stripe] PRO renovado userId={} até={}", userId, fim);

        } catch (Exception e) {
            log.error("[Stripe] Erro em subscription.updated", e);
        }
    }

    // ── customer.subscription.deleted ─────────────────────────────────────

    private void processarAssinaturaCancelada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                            .getObject().orElse(null);

            if (stripeSub == null) {
                log.warn("[Stripe] Subscription nula em subscription.deleted");
                return;
            }

            log.info("[Stripe] Subscription cancelada/expirada: id={}", stripeSub.getId());

            // Executa a expiração das assinaturas vencidas (já existia)
            subscriptionService.expirarAssinaturasVencidas();

        } catch (Exception e) {
            log.error("[Stripe] Erro em subscription.deleted", e);
        }
    }

    // ── payment_intent.succeeded (legado) ─────────────────────────────────

    private void processarPagamentoAprovado(Event event) {
        try {
            PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                    .getObject().orElse(null);

            if (paymentIntent == null) return;

            log.info("[Stripe] PaymentIntent aprovado: id={}", paymentIntent.getId());

            Map<String, String> metadata = paymentIntent.getMetadata();
            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata do paymentIntent={}", paymentIntent.getId());
                return;
            }

            LocalDate inicio = LocalDate.now();
            LocalDate fim    = inicio.plusDays(30);
            subscriptionService.ativarPro(userId, paymentIntent.getId(), null, inicio, fim);
            log.info("[Stripe] PRO ativado via payment_intent userId={} até={}", userId, fim);

        } catch (Exception e) {
            log.error("[Stripe] Erro em payment_intent.succeeded", e);
        }
    }

    // ── Helper ─────────────────────────────────────────────────────────────

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
