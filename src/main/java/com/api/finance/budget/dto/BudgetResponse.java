package com.api.finance.budget.dto;

import com.api.finance.budget.model.Budget;

import java.math.BigDecimal;
import java.util.UUID;

public record BudgetResponse(
        UUID id,
        UUID categoryId,
        String categoryNome,
        BigDecimal valorLimite,
        BigDecimal valorGasto,
        double percentualGasto,
        int mes,
        int ano,
        Integer alertaEm
) {
    public static BudgetResponse de(Budget b) {
        return new BudgetResponse(
                b.getId(),
                b.getCategory() != null ? b.getCategory().getId() : null,
                b.getCategory() != null ? b.getCategory().getNome() : "Geral",
                b.getValorLimite(),
                b.getValorGasto(),
                b.getPercentualGasto(),
                b.getMes(),
                b.getAno(),
                b.getAlertaEm()
        );
    }
}
