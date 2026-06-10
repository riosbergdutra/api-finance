package com.api.finance.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resposta consolidada do dashboard.
 * Composta por 4 queries executadas em paralelo via CompletableFuture.
 */
public record DashboardResponse(
        BigDecimal saldoTotal,
        BigDecimal receitasMes,
        BigDecimal despesasMes,
        long notificacoesNaoLidas,
        long contasAtivas,
        long metasEmAndamento
) {}