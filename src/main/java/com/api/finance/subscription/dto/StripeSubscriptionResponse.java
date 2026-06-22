package com.api.finance.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da API GET /v1/subscriptions/{id} do Stripe (assinatura recorrente).
 *
 * Documentação: https://stripe.com/docs/api/subscriptions/object
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeSubscriptionResponse(
        String id,
        String status,                    // "active", "past_due", "unpaid", "canceled", "incomplete"
        @JsonProperty("current_period_end") Long currentPeriodEnd,  // Unix timestamp
        Metadata metadata
) {
    /**
     * Metadata da subscription.
     * Deve conter: { "user_id": "<uuid-interno>" }
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("user_id") String userId
    ) {}
}