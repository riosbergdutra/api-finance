package com.api.finance.openfinance.dto;

/**
 * Resultado de uma sincronização de transações via Open Finance.
 */
public record OpenFinanceSyncResponse(
        int importadas,    // Transações novas importadas com sucesso
        int ignoradas,     // Transações ignoradas por já existirem (deduplicação)
        int erros          // Transações que falharam ao importar
) {}
