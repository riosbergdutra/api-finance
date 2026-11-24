package com.api.finance.subscription.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;

    /** Stripe Event.created of the last applied recurring-subscription event. */
    @Column(name = "stripe_last_event_created")
    private Long stripeLastEventCreated;

    @Column(name = "stripe_last_event_id")
    private String stripeLastEventId;

    public long diasRestantes() {
        if (fimPeriodo == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), fimPeriodo);
    }

    public boolean isPro() {
        return PlanType.PRO.equals(plano)
                && SubscriptionStatus.ACTIVE.equals(status)
                && fimPeriodo != null
                && !fimPeriodo.isBefore(LocalDate.now());
    }

    public boolean isExpired() {
        return SubscriptionStatus.EXPIRED.equals(status);
    }

    public boolean isCanceled() {
        return SubscriptionStatus.CANCELLED.equals(status);
    }
}
