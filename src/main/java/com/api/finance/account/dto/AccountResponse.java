package com.api.finance.account.dto;

import com.api.finance.account.model.Account;
import com.api.finance.account.model.AccountType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * DTO de resposta — dados seguros para exposição via API.
 *
 * SEGURANÇA: Não expõe userId, keycloakId ou dados internos de infraestrutura.
 */
public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        BigDecimal balance,
        String currency,
        String color,
        String icon,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountResponse de(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getBalance(),
                account.getCurrency(),
                account.getColor(),
                account.getIcon(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
