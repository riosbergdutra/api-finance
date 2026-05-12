package com.api.finance.transaction.dto;

import com.api.finance.transaction.model.TransactionStatus;
import com.api.finance.transaction.model.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(

        UUID categoryId,

        @NotNull
        TransactionType tipo,

        @NotNull
        TransactionStatus status,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal valor,

        @Size(max = 200)
        String descricao,

        @Size(max = 100)
        String estabelecimento,

        @NotNull
        LocalDate data
) {}
