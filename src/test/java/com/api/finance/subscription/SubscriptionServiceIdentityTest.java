package com.api.finance.subscription;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.events.FinanceEvents;
import com.api.finance.subscription.dto.SubscriptionResponse;
import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.exception.PlanLimitExceededException;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.model.SubscriptionStatus;
import com.api.finance.subscription.repository.SubscriptionRepository;
import com.api.finance.subscription.service.StripeService;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.user.application.port.UserDirectoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceIdentityTest {

    private static final UUID KEYCLOAK_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID INTERNAL_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private UserDirectoryPort userDirectory;
    @Mock
    private StripeService stripeService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionService(
                subscriptionRepository,
                userDirectory,
                stripeService,
                eventPublisher
        );
    }

    @Test
    void getMeuPlanoMustResolveInternalUserIdFromKeycloakSubject() {
        Subscription free = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(userDirectory.findInternalUserId(KEYCLOAK_ID)).thenReturn(Optional.of(INTERNAL_USER_ID));
        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.of(free));

        SubscriptionResponse response = service.getMeuPlano(new AuthenticatedUser(KEYCLOAK_ID));

        assertEquals(PlanType.FREE, response.plano());
        assertFalse(response.isPro());
        verify(subscriptionRepository).insertFreeIfAbsent(any(UUID.class), org.mockito.ArgumentMatchers.eq(INTERNAL_USER_ID));
        verify(subscriptionRepository).findByUserId(INTERNAL_USER_ID);
    }

    @Test
    void lockSubscriptionForQuotaCheckMustCreateOrFindAndLockThePlanRow() {
        Subscription free = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findByUserIdForUpdate(INTERNAL_USER_ID))
                .thenReturn(Optional.of(free));

        service.lockSubscriptionForQuotaCheck(INTERNAL_USER_ID);

        verify(subscriptionRepository).insertFreeIfAbsent(any(UUID.class),
                org.mockito.ArgumentMatchers.eq(INTERNAL_USER_ID));
        verify(subscriptionRepository).findByUserIdForUpdate(INTERNAL_USER_ID);
    }

    @Test
    void readOnlyPlanChecksTreatMissingSubscriptionAsFreeWithoutCreatingRows() {
        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.empty());

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertIsPro(INTERNAL_USER_ID, "Exportação PDF"));
        assertThrows(PlanLimitExceededException.class,
                () -> service.assertPodeCriarTransacao(INTERNAL_USER_ID, 100));

        verify(subscriptionRepository, never()).insertFreeIfAbsent(any(UUID.class), any(UUID.class));
    }

    @Test
    void planoProComPeriodoExpiradoNaoDeveSerConsideradoAtivo() {
        Subscription expiredByDate = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .fimPeriodo(LocalDate.now().minusDays(1))
                .build();
        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.of(expiredByDate));

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertIsPro(INTERNAL_USER_ID, "Exportação PDF"));
    }

    @Test
    void planoProSemFimDePeriodoDeveFalharFechado() {
        Subscription withoutPeriod = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.of(withoutPeriod));

        assertThrows(PlanLimitExceededException.class,
                () -> service.assertIsPro(INTERNAL_USER_ID, "Exportação PDF"));
    }

    @Test
    void cancelarAssinaturaMustPublishNotificationEventAfterSuccessfulOperation() {
        Subscription pro = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .fimPeriodo(LocalDate.now().plusDays(30))
                .build();

        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.of(pro));
        when(userDirectory.findIdentityProviderId(INTERNAL_USER_ID)).thenReturn(Optional.of(KEYCLOAK_ID));

        service.cancelarSubscricao(INTERNAL_USER_ID);

        verify(subscriptionRepository).save(pro);
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof FinanceEvents.AssinaturaCanceladaEvent canceled
                        && KEYCLOAK_ID.toString().equals(canceled.keycloakId())
                        && INTERNAL_USER_ID.equals(canceled.userId())
        ));
    }

    @Test
    void stripeCancellationMustExpireOnlyTheMatchingSubscription() {
        Subscription pro = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.PRO)
                .status(SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId("sub_target")
                .fimPeriodo(LocalDate.now().plusDays(30))
                .build();
        when(subscriptionRepository.findByStripeSubscriptionId("sub_target"))
                .thenReturn(Optional.of(pro));
        when(userDirectory.findIdentityProviderId(INTERNAL_USER_ID))
                .thenReturn(Optional.of(KEYCLOAK_ID));

        service.expirarPorStripeSubscriptionId("sub_target");

        assertEquals(PlanType.FREE, pro.getPlano());
        assertEquals(SubscriptionStatus.EXPIRED, pro.getStatus());
        verify(subscriptionRepository).save(pro);
        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof FinanceEvents.AssinaturaExpiradaEvent expired
                        && KEYCLOAK_ID.toString().equals(expired.keycloakId())
                        && INTERNAL_USER_ID.equals(expired.userId())
        ));
    }

    @Test
    void ativarProMustResolveKeycloakIdFromInternalUserIdForDomainEvent() {
        Subscription free = Subscription.builder()
                .userId(INTERNAL_USER_ID)
                .plano(PlanType.FREE)
                .status(SubscriptionStatus.ACTIVE)
                .build();
        LocalDate inicio = LocalDate.of(2026, 11, 21);
        LocalDate fim = inicio.plusDays(30);

        when(subscriptionRepository.findByUserId(INTERNAL_USER_ID)).thenReturn(Optional.of(free));
        when(userDirectory.findIdentityProviderId(INTERNAL_USER_ID)).thenReturn(Optional.of(KEYCLOAK_ID));

        service.ativarPro(INTERNAL_USER_ID, "pi_test", "sub_test", inicio, fim);

        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof FinanceEvents.ProAtivadoEvent pro
                        && KEYCLOAK_ID.toString().equals(pro.keycloakId())
                        && INTERNAL_USER_ID.equals(pro.userId())
        ));
    }
}
