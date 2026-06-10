package com.api.finance.bff.controller;

import com.api.finance.subscription.service.MercadoPagoWebhookService;
import com.api.finance.bff.dto.MercadoPagoWebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Recebe notificações do Mercado Pago (webhooks).
 *
 * FLUXO:
 * 1. Usuário assina o plano PRO no frontend (via SDK MP)
 * 2. MP chama este endpoint com o evento de pagamento/assinatura
 * 3. Validamos a assinatura HMAC (x-signature header) para garantir autenticidade
 * 4. Delegamos ao MercadoPagoWebhookService conforme o tipo de evento
 *
 * SEGURANÇA:
 * - Validação obrigatória de assinatura HMAC-SHA256 (rejeita qualquer request sem assinatura válida)
 * - Endpoint público (sem JWT) — autenticação é feita pela assinatura MP
 * - Sempre responde 200 rapidamente; processamento pesado vai para @Async
 *
 * CONFIGURAÇÃO NECESSÁRIA (application.yaml):
 *   mercadopago:
 *     webhook-secret: <sua-chave-secreta-do-painel-mp>
 *     access-token: <seu-access-token-mp>
 */
@RestController
@RequestMapping("/webhooks/mercadopago")
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService webhookService;

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    /**
     * Endpoint principal de recebimento de webhooks.
     *
     * O Mercado Pago envia um header x-signature no formato:
     *   ts=<timestamp>,v1=<hmac-sha256-hex>
     *
     * A assinatura cobre: "id:<event_id>;request-id:<x-request-id>;ts:<ts>;"
     */
    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody MercadoPagoWebhookPayload payload) {

        log.info("[MP Webhook] Recebido: type={} action={} id={}",
                payload.type(), payload.action(), payload.data() != null ? payload.data().id() : "null");

        // Valida assinatura antes de qualquer processamento
        if (!validarAssinatura(xSignature, xRequestId, payload)) {
            log.warn("[MP Webhook] Assinatura inválida — request rejeitado. xRequestId={}", xRequestId);
            return ResponseEntity.ok().build(); // Retorna 200 mesmo assim (evita retry do MP)
        }

        webhookService.processar(payload);

        return ResponseEntity.ok().build();
    }

    /**
     * Valida o header x-signature do Mercado Pago.
     *
     * Formato do header: "ts=1704067200,v1=abc123..."
     * String a assinar: "id:<data.id>;request-id:<x-request-id>;ts:<ts>;"
     */
    private boolean validarAssinatura(String xSignature, String xRequestId, MercadoPagoWebhookPayload payload) {
        if (xSignature == null || xSignature.isBlank()) {
            log.warn("[MP Webhook] Header x-signature ausente");
            return false;
        }

        try {
            // Extrai ts e v1 do header
            String ts = null;
            String v1 = null;
            for (String part : xSignature.split(",")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    if ("ts".equals(kv[0].trim())) ts = kv[1].trim();
                    if ("v1".equals(kv[0].trim())) v1 = kv[1].trim();
                }
            }

            if (ts == null || v1 == null) return false;

            // Monta a string de validação
            String dataId = payload.data() != null ? payload.data().id() : "";
            String manifest = "id:" + dataId + ";request-id:" + (xRequestId != null ? xRequestId : "") + ";ts:" + ts + ";";

            // Calcula HMAC-SHA256
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String calculado = java.util.HexFormat.of().formatHex(hmacBytes);

            boolean valido = MessageDigest.isEqual(calculado.getBytes(), v1.getBytes());
            if (!valido) {
                log.warn("[MP Webhook] HMAC não confere. Esperado={} Calculado={}", v1, calculado);
            }
            return valido;

        } catch (Exception e) {
            log.error("[MP Webhook] Erro ao validar assinatura: {}", e.getMessage());
            return false;
        }
    }
}