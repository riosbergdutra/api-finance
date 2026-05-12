package com.api.finance.transaction.dto;

import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransactionRequest(

        @NotNull(message = "Conta é obrigatória")
        UUID accountId,

        UUID contaDestinoId,

        UUID categoryId,

        @NotNull(message = "Tipo é obrigatório")
        TransactionType tipo,

        TransactionStatus status,

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "Valor deve ser positivo")
        @Digits(integer = 17, fraction = 2)
        BigDecimal valor,

        @Size(max = 200)
        String descricao,

        @Size(max = 100)
        String estabelecimento,

        @NotNull(message = "Data é obrigatória")
        LocalDate data
) {}
