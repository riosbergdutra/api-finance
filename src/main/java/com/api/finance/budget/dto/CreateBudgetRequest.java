package com.api.finance.budget.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetRequest(

        UUID categoryId,

        @NotNull(message = "Valor limite é obrigatório")
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal valorLimite,

        @NotNull @Min(1) @Max(12)
        Integer mes,

        @NotNull @Min(2000)
        Integer ano,

        @Min(1) @Max(100)
        Integer alertaEm
) {}
