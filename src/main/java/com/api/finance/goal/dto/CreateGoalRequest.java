package com.api.finance.goal.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateGoalRequest(

        @NotBlank @Size(max = 100)
        String nome,

        @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2)
        BigDecimal valorAlvo,

        LocalDate dataAlvo,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB")
        String cor,

        @Size(max = 50)
        String icone
) {}
