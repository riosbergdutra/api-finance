package com.api.finance.category.dto;

import com.api.finance.category.model.Category;
import com.api.finance.category.model.CategoryType;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String nome,
        CategoryType tipo,
        String icone,
        String cor,
        UUID paiId,
        boolean sistema
) {
    public static CategoryResponse de(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getNome(),
                c.getTipo(),
                c.getIcone(),
                c.getCor(),
                c.getPai() != null ? c.getPai().getId() : null,
                c.isSistema()
        );
    }
}
