package com.api.finance.subscription.service;

import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Processa eventos do Stripe e aciona o SubscriptionService.
 *
 * FIX: getDataObjectDeserializer().getObject() retorna Optional.empty() quando
 * a versão da API do evento (gerado pelo dashboard/CLI) difere da versão do SDK.
 * Stripe-java 30.x usa uma API version específica em compile-time; eventos gerados
 * com versão diferente falham na desserialização tipada.
 *
 * Solução: usar deserializeUnsafe() que ignora a versão e desserializa mesmo assim,
 * com fallback para getRawJson() + parse manual quando necessário.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final SubscriptionService subscriptionService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void processar(Event event) {
        if (event == null || event.getType() == null) {
            log.warn("[Stripe] Evento nulo ou sem tipo");
            return;
        }

        log.debug("[Stripe] Processando evento: type={} apiVersion={}", event.getType(), event.getApiVersion());

        int inserted = jdbcTemplate.update("""
                INSERT INTO stripe_webhook_events (event_id, event_type, received_at)
                VALUES (?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (event_id) DO NOTHING
                """, event.getId(), event.getType());

        if (inserted == 0) {
            log.info("[Stripe] Evento duplicado ignorado: eventId={} type={}", event.getId(), event.getType());
            return;
        }

        // A marca de idempotência e as alterações de assinatura participam da
        // mesma transação SQL. Se o processamento falhar, ambas sofrem rollback
        // e o Stripe poderá reenviar o webhook.
        switch (event.getType()) {
            case "checkout.session.completed"      -> processarCheckoutConcluido(event);
            case "customer.subscription.created",
                 "customer.subscription.updated"  -> processarAssinaturaAtualizada(event);
            case "customer.subscription.deleted"  -> processarAssinaturaCancelada(event);
            case "payment_intent.succeeded"       -> processarPagamentoAprovado(event);
            default -> log.debug("[Stripe] Evento ignorado: {}", event.getType());
        }
    }

    // ── checkout.session.completed ─────────────────────────────────────────

    private void processarCheckoutConcluido(Event event) {
        try {
            Session session = deserializar(event, Session.class);

            if (session == null) {
                throw new IllegalStateException("Não foi possível desserializar Session em checkout.session.completed.");
            }

            log.info("[Stripe] Checkout concluído: sessionId={} mode={}", session.getId(), session.getMode());

            Map<String, String> metadata = session.getMetadata();
            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata da session={}", session.getId());
                return;
            }

            // mode=payment → pagamento único, ativa PRO por 30 dias imediatamente
            // mode=subscription → o Stripe vai disparar customer.subscription.created/updated
            //                     com as datas exatas do período — apenas logamos aqui
            if ("payment".equals(session.getMode())) {
                LocalDate inicio = LocalDate.now();
                LocalDate fim    = inicio.plusDays(30);
                subscriptionService.ativarPro(userId, session.getPaymentIntent(), null, inicio, fim);
                log.info("[Stripe] PRO ativado via pagamento único userId={} até={}", userId, fim);
            } else {
                log.info("[Stripe] Checkout subscription concluído userId={} — aguardando subscription event", userId);
            }

        } catch (Exception e) {
            throw new IllegalStateException("Falha ao processar checkout.session.completed.", e);
        }
    }

    // ── customer.subscription.created / updated ────────────────────────────

    private void processarAssinaturaAtualizada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    deserializar(event, com.stripe.model.Subscription.class);

            if (stripeSub == null) {
                throw new IllegalStateException("Não foi possível desserializar Subscription em " + event.getType() + ".");
            }

            log.info("[Stripe] Assinatura {}: id={} status={}", event.getType(), stripeSub.getId(), stripeSub.getStatus());

            if (!"active".equals(stripeSub.getStatus())) {
                log.info("[Stripe] Subscription não está ativa (status={}), ignorando", stripeSub.getStatus());
                return;
            }

            Map<String, String> metadata = stripeSub.getMetadata();
            UUID userId = resolverUserId(metadata != null ? metadata.get("userId") : null);

            if (userId == null) {
                log.warn("[Stripe] userId não encontrado no metadata da subscription={}", stripeSub.getId());
                return;
            }

            // Pega o fim do período da primeira item da subscription
            Long periodEnd = null;
            try {
                periodEnd = stripeSub.getItems()
                        .getData().get(0)
                        .getCurrentPeriodEnd();
            } catch (Exception ex) {
                log.warn("[Stripe] Não foi possível ler currentPeriodEnd da subscription={}: {}", stripeSub.getId(), ex.getMessage());
            }

            LocalDate fim;
            if (periodEnd != null) {
                fim = Instant.ofEpochSecond(periodEnd)
                        .atZone(ZoneId.systemDefault()).toLocalDate();
            } else {
                // Fallback: 30 dias a partir de hoje se não conseguir ler o período
                fim = LocalDate.now().plusDays(30);
                log.warn("[Stripe] currentPeriodEnd nulo, usando fallback de 30 dias. subscription={}", stripeSub.getId());
            }

            LocalDate inicio = LocalDate.now();
            subscriptionService.ativarPro(userId, null, stripeSub.getId(), inicio, fim);
            log.info("[Stripe] PRO ativado/renovado userId={} até={}", userId, fim);

        } catch (Exception e) {
            throw new IllegalStateException("Falha ao processar " + event.getType() + ".", e);
        }
    }

    // ── customer.subscription.deleted ─────────────────────────────────────

    private void processarAssinaturaCancelada(Event event) {
        try {
            com.stripe.model.Subscription stripeSub =
                    deserializar(event, com.stripe.model.Subscription.class);

            if (stripeSub == null) {
                throw new IllegalStateException("Não foi possível desserializar Subscription em subscription.deleted.");
            }

            log.info("[Stripe] Subscription cancelada/expirada: id={}", stripeSub.getId());
            subscriptionService.expirarPorStripeSubscriptionId(stripeSub.getId());

        } catch (Exception e) {
            throw new IllegalStateException("Falha ao processar customer.subscription.deleted.", e);
        }
    }

    // ── payment_intent.succeeded (legado / pagamento único) ───────────────

    private void processarPagamentoAprovado(Event event) {
        try {
            PaymentIntent paymentIntent = deserializar(event, PaymentIntent.class);

            if (paymentIntent == null) {
                throw new IllegalStateException("Não foi possível desserializar PaymentIntent.");
            }

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
            throw new IllegalStateException("Falha ao processar payment_intent.succeeded.", e);
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Desserializa o objeto do evento com fallback para deserializeUnsafe().
     *
     * getObject() falha silenciosamente (retorna Optional.empty()) quando a versão
     * da API do evento não bate com a versão compilada do SDK. deserializeUnsafe()
     * ignora essa verificação de versão e tenta o parse mesmo assim — é o comportamento
     * correto para webhooks recebidos de contas que podem estar em versão diferente.
     */
    @SuppressWarnings("unchecked")
    private <T extends StripeObject> T deserializar(Event event, Class<T> tipo) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        // Tentativa 1: desserialização padrão (funciona quando versões batem)
        if (deserializer.getObject().isPresent()) {
            StripeObject obj = deserializer.getObject().get();
            if (tipo.isInstance(obj)) {
                return tipo.cast(obj);
            }
        }

        // Tentativa 2: deserializeUnsafe — ignora incompatibilidade de versão da API
        try {
            StripeObject obj = deserializer.deserializeUnsafe();
            if (tipo.isInstance(obj)) {
                log.debug("[Stripe] Usado deserializeUnsafe para evento type={} apiVersion={}",
                        event.getType(), event.getApiVersion());
                return tipo.cast(obj);
            }
        } catch (Exception e) {
            log.warn("[Stripe] deserializeUnsafe falhou para {}: {}", event.getType(), e.getMessage());
        }

        return null;
    }

    private UUID resolverUserId(String reference) {
        if (reference == null || reference.isBlank()) return null;
        try {
            return UUID.fromString(reference);
        } catch (Exception e) {
            log.warn("[Stripe] UUID inválido no metadata: {}", reference);
            return null;
        }
    }
}