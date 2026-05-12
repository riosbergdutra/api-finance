package com.api.finance.transaction.dto;

import com.api.finance.transaction.model.Transaction;
import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        UUID contaDestinoId,
        UUID categoryId,
        String categoryNome,
        TransactionType tipo,
        TransactionStatus status,
        BigDecimal valor,
        String descricao,
        String estabelecimento,
        LocalDate data,
        OffsetDateTime criadoEm
) {
    public static TransactionResponse de(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAccountId(),
                t.getContaDestinoId(),
                t.getCategory() != null ? t.getCategory().getId() : null,
                t.getCategory() != null ? t.getCategory().getNome() : null,
                t.getTipo(),
                t.getStatus(),
                t.getValor(),
                t.getDescricao(),
                t.getEstabelecimento(),
                t.getData(),
                t.getCriadoEm()
        );
    }
}
