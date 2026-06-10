package com.api.finance.subscription.service;

import com.api.finance.bff.dto.MercadoPagoWebhookPayload;
import com.api.finance.subscription.dto.MercadoPagoPaymentResponse;
import com.api.finance.subscription.dto.MercadoPagoSubscriptionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa eventos do Mercado Pago e aciona o SubscriptionService.
 *
 * EVENTOS TRATADOS:
 *
 * payment / payment.updated:
 *   - Busca o pagamento na API do MP pelo ID
 *   - Se status == "approved": ativa PRO por 30 dias
 *   - Se status == "refunded" / "cancelled": volta para FREE (chargeback)
 *
 * subscription_preapproval / updated:
 *   - Busca a assinatura na API do MP
 *   - Se status == "authorized": renova o período PRO
 *   - Se status == "cancelled" / "paused": revoga o PRO
 *
 * PROCESSAMENTO @Async:
 *   O controller responde 200 imediatamente. Este service roda em thread separada.
 *   Isso evita timeout de 5s que o MP impõe para considerar o webhook entregue.
 *
 * IDEMPOTÊNCIA:
 *   O MP pode enviar o mesmo evento mais de uma vez (retry em caso de falha).
 *   O SubscriptionService.ativarPro() é idempotente — sobrescrever com os
 *   mesmos valores não causa problema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookService {

    private final SubscriptionService subscriptionService;
    private final RestClient restClient;

    @Value("${mercadopago.access-token}")
    private String accessToken;

    /**
     * Ponto de entrada — chamado pelo controller de forma @Async.
     */
    @Async
    public void processar(MercadoPagoWebhookPayload payload) {
        if (payload.data() == null || payload.data().id() == null) {
            log.warn("[MP] Payload sem data.id — ignorado");
            return;
        }

        try {
            switch (payload.type()) {
                case "payment" -> processarPagamento(payload.data().id());
                case "subscription_preapproval" -> processarAssinatura(payload.data().id());
                default -> log.debug("[MP] Tipo de evento ignorado: {}", payload.type());
            }
        } catch (Exception e) {
            log.error("[MP] Erro ao processar webhook type={} id={}: {}",
                    payload.type(), payload.data().id(), e.getMessage(), e);
        }
    }

    // ── Pagamento avulso ──────────────────────────────────────────────────

    private void processarPagamento(String paymentId) {
        log.info("[MP] Processando pagamento id={}", paymentId);

        MercadoPagoPaymentResponse payment = buscarPagamento(paymentId).orElse(null);
        if (payment == null) {
            log.warn("[MP] Pagamento não encontrado na API: id={}", paymentId);
            return;
        }

        UUID userId = resolverUserId(payment.metadata());
        if (userId == null) {
            log.warn("[MP] userId não encontrado no metadata do pagamento id={}", paymentId);
            return;
        }

        switch (payment.status()) {
            case "approved" -> {
                LocalDate inicio = LocalDate.now();
                LocalDate fim = inicio.plusDays(30);
                subscriptionService.ativarPro(userId, paymentId, null, inicio, fim);
                log.info("[MP] PRO ativado via pagamento: userId={} paymentId={}", userId, paymentId);
            }
            case "refunded", "cancelled", "charged_back" -> {
                subscriptionService.expirarAssinaturasVencidas(); // força recheck
                log.info("[MP] Pagamento revertido — userId={} status={}", userId, payment.status());
            }
            default -> log.debug("[MP] Status de pagamento não tratado: {}", payment.status());
        }
    }

    // ── Assinatura recorrente ─────────────────────────────────────────────

    private void processarAssinatura(String subscriptionId) {
        log.info("[MP] Processando assinatura id={}", subscriptionId);

        MercadoPagoSubscriptionResponse subscription = buscarAssinatura(subscriptionId).orElse(null);
        if (subscription == null) {
            log.warn("[MP] Assinatura não encontrada na API: id={}", subscriptionId);
            return;
        }

        UUID userId = resolverUserId(subscription.metadata());
        if (userId == null) {
            log.warn("[MP] userId não encontrado no metadata da assinatura id={}", subscriptionId);
            return;
        }

        switch (subscription.status()) {
            case "authorized" -> {
                LocalDate inicio = LocalDate.now();
                LocalDate fim = subscription.nextPaymentDate() != null
                        ? subscription.nextPaymentDate()
                        : inicio.plusMonths(1);
                subscriptionService.ativarPro(userId, null, subscriptionId, inicio, fim);
                log.info("[MP] PRO renovado via assinatura: userId={} subId={} até={}", userId, subscriptionId, fim);
            }
            case "cancelled", "paused" -> {
                subscriptionService.expirarAssinaturasVencidas();
                log.info("[MP] Assinatura cancelada/pausada — userId={} status={}", userId, subscription.status());
            }
            default -> log.debug("[MP] Status de assinatura não tratado: {}", subscription.status());
        }
    }

    // ── API Mercado Pago ──────────────────────────────────────────────────

    private Optional<MercadoPagoPaymentResponse> buscarPagamento(String paymentId) {
        try {
            MercadoPagoPaymentResponse response = restClient.get()
                    .uri("https://api.mercadopago.com/v1/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MercadoPagoPaymentResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            log.error("[MP] Falha ao buscar pagamento id={}: {}", paymentId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<MercadoPagoSubscriptionResponse> buscarAssinatura(String subscriptionId) {
        try {
            MercadoPagoSubscriptionResponse response = restClient.get()
                    .uri("https://api.mercadopago.com/preapproval/{id}", subscriptionId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(MercadoPagoSubscriptionResponse.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            log.error("[MP] Falha ao buscar assinatura id={}: {}", subscriptionId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Extrai o userId interno do metadata do pagamento/assinatura.
     *
     * COMO ENVIAR O METADATA:
     * Ao criar a preferência de pagamento no frontend/backend, inclua:
     *   "metadata": { "user_id": "<uuid-interno-do-usuario>" }
     *
     * Isso garante que o webhook sabe a qual usuário o pagamento pertence.
     */
    private UUID resolverUserId(MercadoPagoPaymentResponse.Metadata metadata) {
        if (metadata == null || metadata.userId() == null) return null;
        try {
            return UUID.fromString(metadata.userId());
        } catch (IllegalArgumentException e) {
            log.warn("[MP] user_id inválido no metadata: {}", metadata.userId());
            return null;
        }
    }

    private UUID resolverUserId(MercadoPagoSubscriptionResponse.Metadata metadata) {
        if (metadata == null || metadata.userId() == null) return null;
        try {
            return UUID.fromString(metadata.userId());
        } catch (IllegalArgumentException e) {
            log.warn("[MP] user_id inválido no metadata: {}", metadata.userId());
            return null;
        }
    }
}