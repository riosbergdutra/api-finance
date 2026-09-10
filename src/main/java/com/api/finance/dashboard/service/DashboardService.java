package com.api.finance.dashboard.service;

import com.api.finance.account.repository.AccountRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.dashboard.dto.DashboardResponse;
import com.api.finance.goal.repository.GoalRepository;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Serviço do Dashboard.
 *
 * As 4 queries são executadas em PARALELO via CompletableFuture.
 * Com Virtual Threads (habilitadas em FinanceApplication via TomcatProtocolHandlerCustomizer),
 * cada query roda em sua própria thread virtual — sem bloquear thread de plataforma.
 *
 * Sequencial: ~200ms + ~150ms + ~150ms + ~100ms = ~600ms
 * Em paralelo: max(~200ms, ~150ms, ~150ms, ~100ms) = ~200ms
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationRepository notificationRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    public DashboardResponse getDashboard(AuthenticatedUser caller) {
        UUID userId = userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));

        // Primeiro e último dia do mês atual — compatível com PostgreSQL
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDate fimMes = inicioMes.withDayOfMonth(inicioMes.lengthOfMonth());

        // ── 4 queries em paralelo via CompletableFuture ──────────────────
        CompletableFuture<BigDecimal> saldoFuture =
                CompletableFuture.supplyAsync(() ->
                        accountRepository.sumBalanceByUserId(userId));

        CompletableFuture<BigDecimal> receitasFuture =
                CompletableFuture.supplyAsync(() ->
                        transactionRepository.sumReceitasMes(userId, inicioMes, fimMes));

        CompletableFuture<BigDecimal> despesasFuture =
                CompletableFuture.supplyAsync(() ->
                        transactionRepository.sumDespesasMes(userId, inicioMes, fimMes));

        CompletableFuture<long[]> contadoresFuture =
                CompletableFuture.supplyAsync(() -> new long[]{
                        notificationRepository.countByUserIdAndLidaFalse(userId),
                        accountRepository.countByUserIdAndActiveTrue(userId),
                        goalRepository.findByUserIdAndConcluidaFalse(userId).size()
                });

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