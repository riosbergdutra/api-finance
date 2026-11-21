package com.api.finance.export;

import com.api.finance.export.service.ExportService;
import com.api.finance.export.exception.ExportLimitExceededException;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.transaction.repository.TransactionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static com.api.finance.shared.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExportServiceTest {

    @Mock TransactionRepository transactionRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock UserDirectoryPort userDirectory;

    @InjectMocks ExportService exportService;

    @Test
    void exportRejectsPeriodsThatExceedConfiguredRecordLimit() {
        ReflectionTestUtils.setField(exportService, "maxExportRecords", 1);
        given(userDirectory.findInternalUserId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
        given(transactionRepository.findByUserIdAndPeriodo(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(java.util.List.of(), PageRequest.of(0, 2), 2));

        assertThatThrownBy(() -> exportService.gerarExcel(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                caller()))
                .isInstanceOf(ExportLimitExceededException.class);
    }

    @Test
    void excelExportChecksProPlanAndLoadsTransactionsUsingInternalUserId() {
        given(userDirectory.findInternalUserId(KEYCLOAK_ID)).willReturn(Optional.of(USER_ID));
        given(transactionRepository.findByUserIdAndPeriodo(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .willReturn(Page.empty());

        byte[] result = exportService.gerarExcel(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                caller()
        );

        assertThat(result).isNotEmpty();
        verify(subscriptionService).assertIsPro(USER_ID, "Exportação Excel");
        verify(transactionRepository).findByUserIdAndPeriodo(
                eq(USER_ID), any(LocalDate.class), any(LocalDate.class), any(Pageable.class));
    }
}
