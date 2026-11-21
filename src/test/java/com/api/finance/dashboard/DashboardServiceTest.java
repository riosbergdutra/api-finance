package com.api.finance.dashboard;

import com.api.finance.account.repository.AccountRepository;
import com.api.finance.config.AuthenticatedUser;
import com.api.finance.dashboard.dto.DashboardResponse;
import com.api.finance.dashboard.service.DashboardService;
import com.api.finance.goal.repository.GoalRepository;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Executor;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock GoalRepository goalRepository;
    @Mock UserDirectoryPort userDirectory;
    @Mock Executor dashboardExecutor;

    @InjectMocks DashboardService dashboardService;

    @Test
    void getDashboardUsesDatabaseCountInsteadOfLoadingOpenGoals() {
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(dashboardExecutor).execute(any(Runnable.class));

        AuthenticatedUser caller = caller();
        given(userDirectory.findInternalUserId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
        given(accountRepository.sumBalanceByUserId(USER_ID)).willReturn(new BigDecimal("1250.00"));
        given(transactionRepository.sumReceitasMes(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new BigDecimal("3000.00"));
        given(transactionRepository.sumDespesasMes(org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(new BigDecimal("750.00"));
        given(notificationRepository.countByUserIdAndLidaFalse(USER_ID)).willReturn(2L);
        given(accountRepository.countByUserIdAndActiveTrue(USER_ID)).willReturn(3L);
        given(goalRepository.countByUserIdAndConcluidaFalse(USER_ID)).willReturn(4L);

        DashboardResponse response = dashboardService.getDashboard(caller);

        assertThat(response.saldoTotal()).isEqualByComparingTo("1250.00");
        assertThat(response.receitasMes()).isEqualByComparingTo("3000.00");
        assertThat(response.despesasMes()).isEqualByComparingTo("750.00");
        assertThat(response.notificacoesNaoLidas()).isEqualTo(2L);
        assertThat(response.contasAtivas()).isEqualTo(3L);
        assertThat(response.metasEmAndamento()).isEqualTo(4L);

        verify(goalRepository).countByUserIdAndConcluidaFalse(USER_ID);
        verify(goalRepository, never()).findByUserIdAndConcluidaFalse(USER_ID);
    }
}
