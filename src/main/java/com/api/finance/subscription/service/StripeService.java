package com.api.finance.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integração com Stripe via Checkout Session (modo subscription).
 *
 * FLUXO COMPLETO:
 * 1. Frontend chama  POST /subscriptions/checkout
 * 2. Este service cria uma Checkout Session no Stripe
 * 3. Retorna { url } ao frontend
 * 4. Frontend faz  window.location.href = url
 * 5. Usuário paga na página hospedada pelo Stripe (cartão, PIX, boleto, etc.)
 * 6. Stripe redireciona para  successUrl  (ex: /app/subscription?success=true)
 * 7. Stripe dispara webhook  customer.subscription.updated  →  StripeWebhookService
 * 8. StripeWebhookService chama  subscriptionService.ativarPro(...)
 *
 * POR QUE CHECKOUT SESSION (e não PaymentIntent diretamente)?
 * - PaymentIntent retorna um clientSecret que precisa do Stripe Elements no frontend
 *   (npm install @stripe/stripe-js, form de cartão embutido, tratamento de 3DS...).
 * - Checkout Session devolve uma URL pronta — o Stripe cuida de tudo (formulário,
 *   3DS, PIX, boleto, Apple Pay, Google Pay, retry...).
 * - Para assinaturas recorrentes (mode=subscription), Checkout Session é o fluxo
 *   oficialmente recomendado pelo Stripe.
 *
 * CONFIGURAÇÃO NECESSÁRIA:
 *   stripe.api-key   = sk_test_...   (chave secreta, já está no application-dev.yml)
 *   stripe.price-id  = price_xxx     (criar em dashboard.stripe.com → Products → Prices)
 *   stripe.frontend-url = https://seusite.com  (para success/cancel URLs)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final RestClient restClient;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.price-id}")
    private String stripePriceId;

    @Value("${stripe.frontend-url}")
    private String frontendUrl;

    private static final String STRIPE_BASE = "https://api.stripe.com/v1";

    // ── Checkout Session ───────────────────────────────────────────────────

    /**
     * Cria uma Checkout Session para assinatura mensal recorrente (mode=subscription).
     *
     * O userId é passado no metadata da session — o webhook
     * customer.subscription.updated recebe esse metadata e usa para ativar o PRO.
     *
     * @param userId ID do usuário interno
     * @return URL da página de pagamento hospedada pelo Stripe
     */
    public String criarCheckoutSession(UUID userId) {
        log.info("[Stripe] Criando Checkout Session para userId={}", userId);

        // LinkedHashMap para preservar a ordem dos parâmetros form-encoded
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode",                              "subscription");
        params.put("line_items[0][price]",              stripePriceId);
        params.put("line_items[0][quantity]",           "1");
        params.put("success_url",                       frontendUrl + "/app/subscription?success=true");
        params.put("cancel_url",                        frontendUrl + "/app/subscription?canceled=true");
        // metadata na session → disponível em checkout.session.completed
        // e também propagada para a Subscription criada pelo Stripe
        params.put("subscription_data[metadata][userId]", userId.toString());
        params.put("metadata[userId]",                  userId.toString());

        try {
            String response = restClient.post()
                    .uri(STRIPE_BASE + "/checkout/sessions")
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormBody(params))
                    .retrieve()
                    .body(String.class);

            String sessionId  = extractJsonValue(response, "\"id\"");
            String sessionUrl = extractJsonValue(response, "\"url\"");

            log.info("[Stripe] Checkout Session criada: id={}", sessionId);
            return sessionUrl;

        } catch (Exception e) {
            log.error("[Stripe] Erro ao criar Checkout Session: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar sessão de pagamento no Stripe", e);
        }
    }

    // ── Cancelamento ───────────────────────────────────────────────────────

    /**
     * Cancela uma subscription recorrente no Stripe.
     * O Stripe mantém o acesso até o fim do período vigente (current_period_end).
     * O webhook customer.subscription.deleted é disparado ao final do período.
     */
    public void cancelarSubscription(String subscriptionId) {
        log.info("[Stripe] Cancelando subscription id={}", subscriptionId);

        try {
            restClient.delete()
                    .uri(STRIPE_BASE + "/subscriptions/{id}", subscriptionId)
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .retrieve()
                    .body(String.class);

            log.info("[Stripe] Subscription cancelada: {}", subscriptionId);
        } catch (Exception e) {
            log.error("[Stripe] Erro ao cancelar subscription: {}", e.getMessage());
            // Não propaga — o cancelamento local já foi feito no SubscriptionService
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String buildFormBody(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(key,   StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * Extrai um valor string de um JSON usando busca simples por chave.
     * Suficiente para os campos que precisamos (id, url) sem depender de Jackson.
     * Nota: não trata arrays nem objetos aninhados — apenas strings de primeiro nível.
     */
    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx   = json.indexOf(":", idx + key.length());
        if (colonIdx < 0) return null;
        // Pula espaços entre ":" e o valor
        int quoteStart = json.indexOf("\"", colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd   = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }
}
