package com.api.finance.goal.dto;

import com.api.finance.goal.model.Goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        String nome,
        BigDecimal valorAlvo,
        BigDecimal valorAtual,
        double percentualConcluido,
        LocalDate dataAlvo,
        boolean concluida,
        String cor,
        String icone,
        OffsetDateTime criadoEm
) {
    public static GoalResponse de(Goal g) {
        return new GoalResponse(
                g.getId(), g.getNome(), g.getValorAlvo(), g.getValorAtual(),
                g.getPercentualConcluido(), g.getDataAlvo(), g.isConcluida(),
                g.getCor(), g.getIcone(), g.getCriadoEm()
        );
    }
}
