package com.api.finance.scheduler;

import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.service.NotificationService;
import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.model.SubscriptionStatus;
import com.api.finance.subscription.repository.SubscriptionRepository;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.user.application.port.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpirationSchedulerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID IDENTITY_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private SubscriptionService subscriptionService;
    @Mock private NotificationService notificationService;
    @Mock private UserDirectoryPort userDirectory;

    private SubscriptionExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SubscriptionExpirationScheduler(
                subscriptionRepository, subscriptionService, notificationService, userDirectory
        );
    }

    @Test
    void mustNotifyExpiringSubscriptionUsingPublicUserDirectoryPort() {
        Subscription subscription = Subscription.builder()
                .userId(USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .fimPeriodo(LocalDate.now().plusDays(3))
                .build();
        when(subscriptionRepository.findProExpirandoAte(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(subscription));
        when(userDirectory.findIdentityProviderId(USER_ID)).thenReturn(Optional.of(IDENTITY_ID));

        scheduler.processarExpiracoes();

        verify(notificationService).criarNotificacao(
                eq(IDENTITY_ID.toString()),
                eq(USER_ID),
                eq(NotificationType.ASSINATURA_EXPIRANDO),
                eq("Assinatura PRO expirando"),
                contains("3 dia(s)")
        );
        verify(subscriptionService).expirarAssinaturasVencidas();
        verifyNoInteractions(userDirectory);
    }

    @Test
    void mustSkipNotificationWhenIdentityNoLongerExistsButStillProcessExpirations() {
        Subscription subscription = Subscription.builder()
                .userId(USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .fimPeriodo(LocalDate.now().plusDays(3))
                .build();
        when(subscriptionRepository.findProExpirandoAte(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(subscription));
        when(userDirectory.findIdentityProviderId(USER_ID)).thenReturn(Optional.empty());

        scheduler.processarExpiracoes();

        verifyNoInteractions(notificationService);
        verify(subscriptionService).expirarAssinaturasVencidas();
    }
}
