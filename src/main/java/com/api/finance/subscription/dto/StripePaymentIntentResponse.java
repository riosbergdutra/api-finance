package com.api.finance.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da API GET /v1/payment_intents/{id} do Stripe.
 *
 * Campos relevantes para o FinanceFlow.
 * Os demais campos são ignorados via @JsonIgnoreProperties.
 *
 * Documentação: https://stripe.com/docs/api/payment_intents/object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripePaymentIntentResponse(
        String id,
        String status,                    // "succeeded", "processing", "requires_payment_method", "canceled"
        @JsonProperty("client_secret") String clientSecret,
        Metadata metadata,
        Long amount                       // em centavos (ex: 1990 = R$ 19.90)
) {
    /**
     * Metadata enviado na criação do payment intent.
     * Deve conter: { "user_id": "<uuid-interno>", "plan_type": "pro" }
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("user_id") String userId,
            @JsonProperty("plan_type") String planType
    ) {}
}