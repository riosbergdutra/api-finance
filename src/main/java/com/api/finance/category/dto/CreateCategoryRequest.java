package com.api.finance.category.dto;

import com.api.finance.category.model.CategoryType;
import jakarta.validation.constraints.*;

import java.util.UUID;

public record CreateCategoryRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 80)
        String nome,

        @NotNull(message = "Tipo é obrigatório")
        CategoryType tipo,

        @Size(max = 50)
        String icone,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato #RRGGBB")
        String cor,

        UUID paiId
) {}
