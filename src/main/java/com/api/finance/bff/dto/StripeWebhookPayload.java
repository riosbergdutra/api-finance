package com.api.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload recebido nos webhooks do Stripe.
 *
 * Nota: Na verdade, o Stripe envia o payload como string JSON puro.
 * O controller desserializa usando Webhook.constructEvent().
 *
 * Este DTO é opcional — deixado para referência caso você queira processar manualmente.
 *
 * Exemplo de payload de pagamento aprovado:
 * {
 *   "type": "payment_intent.succeeded",
 *   "id": "evt_123456789",
 *   "data": {
 *     "object": {
 *       "id": "pi_123456789",
 *       "status": "succeeded",
 *       "client_reference_id": "<uuid-usuario>"
 *     }
 *   }
 * }
 *
 * Exemplo de payload de assinatura:
 * {
 *   "type": "customer.subscription.updated",
 *   "id": "evt_987654321",
 *   "data": {
 *     "object": {
 *       "id": "sub_123456789",
 *       "status": "active",
 *       "customer": "<customer-id-stripe>"
 *     }
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripeWebhookPayload(
        String type,
        String id,
        DataPayload data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataPayload(Object object) {}
}
