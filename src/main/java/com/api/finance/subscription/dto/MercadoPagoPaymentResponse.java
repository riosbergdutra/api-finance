package com.api.finance.subscription.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta da API GET /v1/payments/{id} do Mercado Pago.
 *
 * Campos relevantes para o FinanceFlow.
 * Os demais campos são ignorados via @JsonIgnoreProperties.
 *
 * Documentação: https://www.mercadopago.com.br/developers/pt/reference/payments/_payments_id/get
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoPaymentResponse(
        String id,
        String status,            // "approved", "pending", "refunded", "cancelled", "charged_back"
        @JsonProperty("status_detail") String statusDetail,
        Metadata metadata
) {
    /**
     * Metadata enviado na criação da preferência.
     * Deve conter: { "user_id": "<uuid-interno>" }
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(
            @JsonProperty("user_id") String userId
    ) {}
}