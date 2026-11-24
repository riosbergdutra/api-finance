package com.api.finance.subscription.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Integração Stripe restrita ao ambiente de demonstração.
 * Esta aplicação não pode processar cobranças reais nesta fase do produto.
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

    @Value("${stripe.mode:test}")
    private String stripeMode;

    private static final String STRIPE_BASE = "https://api.stripe.com/v1";

    /**
     * Fail-closed: a aplicação não inicia a integração de billing se qualquer
     * configuração indicar modo diferente de teste ou chave live.
     */
    @PostConstruct
    void validarModoDemonstracao() {
        if (!"test".equalsIgnoreCase(stripeMode)
                || stripeApiKey == null
                || !stripeApiKey.startsWith("sk_test_")) {
            throw new IllegalStateException(
                    "FinanceFlow está restrito ao Stripe Test Mode. Configure stripe.mode=test e uma chave sk_test_."
            );
        }
        if (stripePriceId == null || !stripePriceId.startsWith("price_")) {
            throw new IllegalStateException("Configure um Price ID válido do Stripe em modo de teste.");
        }
        log.info("Stripe configurado em modo de teste. Cobranças reais estão bloqueadas por configuração.");
    }

    public String criarCheckoutSession(UUID userId) {
        if (!"test".equalsIgnoreCase(stripeMode) || !stripeApiKey.startsWith("sk_test_")) {
            throw new IllegalStateException("Checkout indisponível: somente o Stripe Test Mode é permitido.");
        }

        log.info("[Stripe DEMO] Criando Checkout Session de teste para userId={}", userId);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("mode", "subscription");
        params.put("line_items[0][price]", stripePriceId);
        params.put("line_items[0][quantity]", "1");
        params.put("success_url", frontendUrl + "/app/subscription?demo=success");
        params.put("cancel_url", frontendUrl + "/app/subscription?demo=canceled");
        params.put("subscription_data[metadata][userId]", userId.toString());
        params.put("metadata[userId]", userId.toString());

        try {
            String response = restClient.post()
                    .uri(STRIPE_BASE + "/checkout/sessions")
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormBody(params))
                    .retrieve()
                    .body(String.class);

            String sessionUrl = extractJsonValue(response, "\"url\"");
            if (sessionUrl == null || !sessionUrl.startsWith("https://checkout.stripe.com/")) {
                throw new IllegalStateException("Stripe não retornou uma URL de Checkout válida.");
            }
            log.info("[Stripe DEMO] Checkout Session de teste criada para userId={}", userId);
            return sessionUrl;
        } catch (Exception e) {
            log.error("[Stripe DEMO] Falha ao criar Checkout Session de teste: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar sessão demonstrativa no Stripe", e);
        }
    }

    public void cancelarSubscription(String subscriptionId) {
        if (!"test".equalsIgnoreCase(stripeMode) || !stripeApiKey.startsWith("sk_test_")) {
            throw new IllegalStateException("Cancelamento Stripe indisponível fora do modo de teste.");
        }
        log.info("[Stripe DEMO] Cancelando subscription de teste id={}", subscriptionId);
        try {
            restClient.delete()
                    .uri(STRIPE_BASE + "/subscriptions/{id}", subscriptionId)
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound alreadyAbsent) {
            // Idempotent deletion: an already absent provider subscription is canceled for our purposes.
        }
    }

    private String buildFormBody(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
              .append("=")
              .append(URLEncoder.encode(value.toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx + key.length());
        if (colonIdx < 0) return null;
        int quoteStart = json.indexOf("\"", colonIdx + 1);
        if (quoteStart < 0) return null;
        int quoteEnd = json.indexOf("\"", quoteStart + 1);
        if (quoteEnd < 0) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }
}
