package com.api.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload recebido nos webhooks do Mercado Pago.
 *
 * Exemplo de payload de pagamento aprovado:
 * {
 *   "type": "payment",
 *   "action": "payment.updated",
 *   "data": { "id": "123456789" }
 * }
 *
 * Exemplo de payload de assinatura:
 * {
 *   "type": "subscription_preapproval",
 *   "action": "updated",
 *   "data": { "id": "SUB-abc123" }
 * }
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) — o MP pode adicionar campos;
 * não queremos quebrar ao receber campos desconhecidos.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoWebhookPayload(
        String type,
        String action,
        DataPayload data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPayload(String id) {}
}