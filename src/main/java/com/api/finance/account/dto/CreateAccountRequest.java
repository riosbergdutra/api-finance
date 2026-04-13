package com.api.finance.account.dto;

import com.api.finance.account.model.AccountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO de criação de conta.
 *
 * SEGURANÇA: Não contém userId nem keycloakId.
 * O userId é sempre extraído do JWT no Service — nunca confiamos no body.
 */
public record CreateAccountRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 1, max = 100, message = "Nome deve ter entre 1 e 100 caracteres")
        String name,

        @NotNull(message = "Tipo é obrigatório")
        AccountType type,

        @DecimalMin(value = "-999999999.99", message = "Saldo inicial inválido")
        @DecimalMax(value = "999999999.99", message = "Saldo inicial inválido")
        @Digits(integer = 17, fraction = 2, message = "Saldo inválido: máximo 17 dígitos inteiros e 2 decimais")
        BigDecimal initialBalance,

        @Pattern(regexp = "^[A-Z]{3}$", message = "Moeda deve ser um código ISO 4217 de 3 letras maiúsculas (ex: BRL)")
        String currency,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal #RRGGBB")
        String color,

        @Size(max = 50, message = "Ícone deve ter no máximo 50 caracteres")
        String icon

) {}
