package com.api.finance.subscription.service;

import com.api.finance.config.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Serviço de integração com Stripe.
 *
 * Responsável por:
 * 1. Criar payment intents (pagamento único - R$ 19,90 por 30 dias PRO)
 * 2. Criar subscriptions (assinatura recorrente mensal)
 * 3. Buscar dados de payment intents e subscriptions
 *
 * DOIS FLUXOS:
 *
 * FLUXO 1 - PAGAMENTO ÚNICO (recomendado para portfólio):
 *   - Cria um payment_intent com amount = 1990 (R$ 19,90 em centavos)
 *   - Frontend usa Stripe Payment Element para coletar cartão
 *   - Após sucesso, webhook "payment_intent.succeeded" ativa PRO por 30 dias
 *   - Simples, sem gestão de assinatura
 *
 * FLUXO 2 - SUBSCRIPTION (mais profissional):
 *   - Cria uma subscription recorrente mensal
 *   - Stripe cobra automaticamente no primeiro dia do mês
 *   - Se falhar, tenta novamente (retry automático)
 *   - Webhook "customer.subscription.updated" renova PRO
 *   - Requer um "plan" (product) pré-criado no Stripe
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    private final RestClient restClient;

    @Value("${stripe.api-key}")
    private String stripeApiKey;

    @Value("${stripe.price-id:#{null}}")  // price_1234567890 (para subscription)
    private String stripePriceId;

    // ── Pagamento Único ────────────────────────────────────────────────────

    /**
     * Cria um payment_intent para pagamento único de R$ 19,90.
     *
     * Retorna o client_secret que deve ser enviado ao frontend,
     * onde o usuário completa o pagamento com Stripe Payment Element.
     *
     * @param userId ID do usuário interno (será armazenado no metadata)
     * @return client_secret para usar no frontend
     */
    public String criarPaymentIntent(UUID userId) {
        log.info("[Stripe] Criando payment_intent para userId={}", userId);

        try {
            // Prepara os parâmetros da requisição (form-encoded, conforme Stripe API)
            Map<String, Object> params = Map.ofEntries(
                    Map.entry("amount", 1990),                 // R$ 19,90 em centavos
                    Map.entry("currency", "brl"),
                    Map.entry("description", "FinanceFlow PRO - 30 dias"),
                    Map.entry("metadata[user_id]", userId.toString()),
                    Map.entry("metadata[plan_type]", "pro")
            );

            // Envia requisição POST /v1/payment_intents
            String response = restClient.post()
                    .uri("https://api.stripe.com/v1/payment_intents")
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(buildFormBody(params))
                    .retrieve()
                    .body(String.class);

            // Extrai client_secret da resposta
            String clientSecret = extractJsonValue(response, "\"client_secret\"");
            log.info("[Stripe] Payment intent criado: {}", extractJsonValue(response, "\"id\""));
            return clientSecret;

        } catch (Exception e) {
            log.error("[Stripe] Erro ao criar payment_intent: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar payment intent no Stripe", e);
        }
    }

    // ── Assinatura Recorrente (alternativa) ────────────────────────────────

    /**
     * Cria uma subscription no Stripe (pagamento mensal recorrente).
     *
     * PREREQUISITO:
     * - Você deve ter criado um "Price" no Stripe (https://dashboard.stripe.com/prices)
     * - O price_id dessa configuração em application.yaml (stripe.price-id)
     * - Exemplo: price_1PxAbCDefGhIjKlMnOpQrSt
     *
     * @param customerId ID do cliente Stripe (obtido após primeiro pagamento)
     * @param userId ID do usuário interno
     * @return subscription_id do Stripe
     */
    public String criarSubscription(String customerId, UUID userId) {
        log.info("[Stripe] Criando subscription para customerId={} userId={}", customerId, userId);

        if (stripePriceId == null || stripePriceId.isBlank()) {
            throw new RuntimeException("stripe.price-id não configurado no application.yaml");
        }

        try {
            Map<String, Object> params = Map.ofEntries(
                    Map.entry("customer", customerId),
                    Map.entry("items[0][price]", stripePriceId),
                    Map.entry("metadata[user_id]", userId.toString())
            );

            String response = restClient.post()
                    .uri("https://api.stripe.com/v1/subscriptions")
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .body(buildFormBody(params))
                    .retrieve()
                    .body(String.class);

            String subscriptionId = extractJsonValue(response, "\"id\"");
            log.info("[Stripe] Subscription criada: {}", subscriptionId);
            return subscriptionId;

        } catch (Exception e) {
            log.error("[Stripe] Erro ao criar subscription: {}", e.getMessage());
            throw new RuntimeException("Falha ao criar subscription no Stripe", e);
        }
    }

    /**
     * Cancela uma subscription (revoga acesso PRO).
     * Stripe mantém o acesso até o final do período vigente (current_period_end).
     */
    public void cancelarSubscription(String subscriptionId) {
        log.info("[Stripe] Cancelando subscription id={}", subscriptionId);

        try {
            restClient.delete()
                    .uri("https://api.stripe.com/v1/subscriptions/{id}", subscriptionId)
                    .header("Authorization", "Bearer " + stripeApiKey)
                    .retrieve()
                    .body(String.class);

            log.info("[Stripe] Subscription cancelada");
        } catch (Exception e) {
            log.error("[Stripe] Erro ao cancelar subscription: {}", e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Constrói um form-urlencoded body (tipo application/x-www-form-urlencoded).
     * Necessário para a API do Stripe.
     */
    private String buildFormBody(Map<String, Object> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((key, value) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(key).append("=").append(java.net.URLEncoder.encode(value.toString(), java.nio.charset.StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * Extrai um valor JSON simples do response string.
     * Para usar com Jackson ObjectMapper em produção.
     */
    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx < 0) return null;
        int quoteIdx = json.indexOf("\"", colonIdx);
        if (quoteIdx < 0) return null;
        int endQuoteIdx = json.indexOf("\"", quoteIdx + 1);
        if (endQuoteIdx < 0) return null;
        return json.substring(quoteIdx + 1, endQuoteIdx);
    }
}