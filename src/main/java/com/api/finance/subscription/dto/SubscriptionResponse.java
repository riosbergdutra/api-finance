package com.api.finance.subscription.dto;

import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.model.SubscriptionStatus;

import java.time.LocalDate;

public record SubscriptionResponse(
        PlanType plano,
        SubscriptionStatus status,
        boolean isPro,
        LocalDate inicioPeriodo,
        LocalDate fimPeriodo,
        long diasParaExpirar,
        // Limites do plano atual
        int limiteContas,
        int limiteTransacoesMes
) {
    public static SubscriptionResponse de(Subscription s) {
        boolean pro = s.isPro();
        return new SubscriptionResponse(
                s.getPlano(),
                s.getStatus(),
                pro,
                s.getInicioPeriodo(),
                s.getFimPeriodo(),
                s.diasRestantes(),
                pro ? Integer.MAX_VALUE : 3,
                pro ? Integer.MAX_VALUE : 100
        );
    }
}
