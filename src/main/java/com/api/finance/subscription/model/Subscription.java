package com.api.finance.subscription.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Assinatura de um usuário.
 *
 * Cada usuário tem exatamente uma Subscription (criada automaticamente no cadastro como FREE).
 * Upgrade para PRO é feito via Mercado Pago — o webhook atualiza este registro.
 *
 * LIMITES DO PLANO FREE:
 * - 3 contas ativas
 * - 100 transações/mês
 * - Sem Open Finance
 * - Sem exportação Excel/PDF
 *
 * Esses limites são verificados em tempo real nos respectivos Services.
 */
@Entity
@Table(name = "subscriptions",
    indexes = @Index(name = "idx_subscription_user", columnList = "user_id", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** FK para users.id — 1:1, não nullable */
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private PlanType plano = PlanType.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** Data de início do período PRO atual */
    @Column(name = "inicio_periodo")
    private LocalDate inicioPeriodo;

    /** Data de expiração do período PRO — null se FREE */
    @Column(name = "fim_periodo")
    private LocalDate fimPeriodo;

    /** ID do pagamento no Mercado Pago — para rastreabilidade */
    @Column(name = "mercado_pago_payment_id", length = 100)
    private String mercadoPagoPaymentId;

    /** ID da assinatura recorrente no Mercado Pago */
    @Column(name = "mercado_pago_subscription_id", length = 100)
    private String mercadoPagoSubscriptionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime atualizadoEm;

    // ── Métodos de domínio ────────────────────────────────────────────────

    public boolean isPro() {
        return PlanType.PRO.equals(plano)
            && SubscriptionStatus.ACTIVE.equals(status)
            && (fimPeriodo == null || !fimPeriodo.isBefore(LocalDate.now()));
    }

    public boolean isFree() {
        return !isPro();
    }

    /** Retorna quantos dias faltam para expirar — -1 se FREE ou sem data */
    public long diasParaExpirar() {
        if (fimPeriodo == null) return -1;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), fimPeriodo);
    }
}
