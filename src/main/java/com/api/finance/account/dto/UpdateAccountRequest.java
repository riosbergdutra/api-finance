package com.api.finance.account.dto;

import com.api.finance.account.model.AccountType;
import jakarta.validation.constraints.*;

/**
 * DTO de atualização de conta.
 *
 * SEGURANÇA: Não permite alterar userId, balance diretamente, nem keycloakId.
 * Saldo só deve ser alterado via transações (crédito/débito), nunca por patch direto.
 */
public record UpdateAccountRequest(

        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 1, max = 100, message = "Nome deve ter entre 1 e 100 caracteres")
        String name,

        @NotNull(message = "Tipo é obrigatório")
        AccountType type,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor deve estar no formato hexadecimal #RRGGBB")
        String color,

        @Size(max = 50, message = "Ícone deve ter no máximo 50 caracteres")
        String icon

) {}
