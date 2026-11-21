package com.api.finance.dashboard.service;

import com.api.finance.account.repository.AccountRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.dashboard.dto.DashboardResponse;
import com.api.finance.goal.repository.GoalRepository;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço do Dashboard.
 *
 * As consultas são disparadas em paralelo via CompletableFuture.supplyAsync().
 * Sem executor explícito, elas usam o ForkJoinPool.commonPool — não o executor
 * de virtual threads configurado no Tomcat. O ganho de latência depende da carga
 * e da capacidade do pool de conexões do banco; não há benchmark assumido aqui.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final GoalRepository goalRepository;
    private final UserDirectoryPort userDirectory;
    private final ThreadPoolTaskExecutor dashboardExecutor;

    public DashboardResponse getDashboard(AuthenticatedUser caller) {
        UUID userId = userDirectory.findInternalUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));

        // Primeiro e último dia do mês atual — compatível com PostgreSQL
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        // ── Consultas em paralelo; contagens executadas no banco ───────────
        CompletableFuture<BigDecimal> saldoFuture =
                CompletableFuture.supplyAsync(() ->
                        accountRepository.sumBalanceByUserId(userId), dashboardExecutor);

        CompletableFuture<BigDecimal> receitasFuture =
                CompletableFuture.supplyAsync(() ->
                        transactionRepository.sumReceitasMes(userId, inicioMes, fimMes), dashboardExecutor);

        CompletableFuture<BigDecimal> despesasFuture =
                CompletableFuture.supplyAsync(() ->
                        transactionRepository.sumDespesasMes(userId, inicioMes, fimMes), dashboardExecutor);

        CompletableFuture<long[]> contadoresFuture =
                CompletableFuture.supplyAsync(() -> new long[]{
                        notificationRepository.countByUserIdAndLidaFalse(userId),
                        accountRepository.countByUserIdAndActiveTrue(userId),
                        goalRepository.countByUserIdAndConcluidaFalse(userId)
                }, dashboardExecutor);

        CompletableFuture.allOf(saldoFuture, receitasFuture, despesasFuture, contadoresFuture).join();

        long[] contadores = contadoresFuture.join();

        return new DashboardResponse(
                saldoFuture.join(),
                receitasFuture.join(),
                despesasFuture.join(),
                contadores[0],
                contadores[1],
                contadores[2]
        );
    }
}