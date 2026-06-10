package com.api.finance.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * Resposta da API GET /preapproval/{id} do Mercado Pago (assinatura recorrente).
 *
 * Documentação: https://www.mercadopago.com.br/developers/pt/reference/subscriptions/_preapproval_id/get
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoSubscriptionResponse(
        String id,
        String status,                          // "authorized", "paused", "cancelled"
        @JsonProperty("next_payment_date") LocalDate nextPaymentDate,
        Metadata metadata
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("user_id") String userId
    ) {}
}