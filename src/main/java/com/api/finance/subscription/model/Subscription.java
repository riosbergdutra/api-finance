package com.api.finance.subscription.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Model de Subscription (assinatura/plano do usuário).
 *
 * FLUXO DE CICLO DE VIDA:
 *
 * 1. Usuário criado → Subscription criada com plano=FREE, status=ACTIVE
 *
 * 2. Usuário clica "Assinar PRO" → StripeService.criarPaymentIntent()
 *    → Frontend coleta cartão com Stripe Payment Element
 *    → Stripe cobra e webhook ativa PRO
 *
 * 3. Webhook "payment_intent.succeeded" ou "customer.subscription.updated"
 *    → ativarPro() muda status para ACTIVE, plano para PRO, seta data de fim
 *
 * 4. Diariamente, SubscriptionExpirationScheduler roda
 *    → Se hoje >= fimPeriodo: expirarAssinaturasVencidas()
 *    → Muda status para EXPIRED, plano volta para FREE
 *
 * 5. Usuário pode cancelar PRO manualmente
 *    → cancelarSubscricao() chama Stripe pra desativar
 *    → Marca status como CANCELED, plano como FREE
 *
 * CAMPOS STRIPE:
 * - stripePaymentIntentId: ID do payment_intent (pagamento único)
 * - stripeSubscriptionId: ID da subscription (recorrente)
 *
 * Ao menos um deles será preenchido quando status=ACTIVE e plano=PRO.
 */
@Entity
@Table(name = "subscription")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plano;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus status;

    @Column(name = "inicio_periodo")
    private LocalDate inicioPeriodo;

    @Column(name = "fim_periodo")
    private LocalDate fimPeriodo;

    // ─── Integração Stripe ─────────────────────────────────────────────────

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    public long diasRestantes() {
        if (fimPeriodo == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), fimPeriodo);
    }

    // ─── Métodos utilitários ────────────────────────────────────────────────

    public boolean isPro() {
        return PlanType.PRO.equals(plano) && SubscriptionStatus.ACTIVE.equals(status);
    }

    public boolean isExpired() {
        return SubscriptionStatus.EXPIRED.equals(status);
    }

    public boolean isCanceled() {
        return SubscriptionStatus.CANCELLED.equals(status);


    }

    // Se mudou de Mercado Pago para Stripe, as colunas antigas
    // podem ser dropadas em migration (ou deixadas para compatibilidade)
    // @Deprecated
    // @Column(name = "mercadopago_payment_id")
    // private String mercadoPagoPaymentId;
    //
    // @Deprecated
    // @Column(name = "mercadopago_subscription_id")
    // private String mercadoPagoSubscriptionId;
}