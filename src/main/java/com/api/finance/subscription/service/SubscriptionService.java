package com.api.finance.subscription.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.service.NotificationService;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.dto.SubscriptionResponse;
import com.api.finance.subscription.exception.PlanLimitExceededException;
import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.model.SubscriptionStatus;
import com.api.finance.subscription.repository.SubscriptionRepository;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gerencia os planos de assinatura e verifica limites em tempo real.
 *
 * LIMITE FREE:
 * - 3 contas ativas
 * - 100 transações/mês
 * - Sem Open Finance (planejado)
 * - Sem exportação Excel/PDF (planejado)
 *
 * Os Services (AccountService, TransactionService) chamam os métodos
 * assertPode*() antes de criar recursos — falhando com 402 Payment Required
 * se o limite for excedido.
 *
 * INTEGRAÇÃO COM STRIPE:
 * - ativarPro() é chamado pelo webhook quando pagamento/subscription é confirmado
 * - cancelarSubscricao() é chamado pelo usuário que quer desativar PRO
 * - expirarAssinaturasVencidas() é executado diariamente pelo scheduler
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private static final int FREE_LIMITE_CONTAS = 3;
    private static final int FREE_LIMITE_TRANSACOES_MES = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final StripeService stripeService;

    // ── Consulta ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public SubscriptionResponse getMeuPlano(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Subscription sub = getOrCreateFree(userId);
        return SubscriptionResponse.de(sub);
    }

    // ── Verificação de limites ────────────────────────────────────────────

    /**
     * Verifica se o usuário pode criar mais uma conta.
     * Lança PlanLimitExceededException (402) se FREE e já tem 3 contas.
     * Chamado pelo AccountService antes de criar.
     */
    @Transactional(readOnly = true)
    public void assertPodeCriarConta(UUID userId, long contasAtuais) {
        if (contasAtuais >= FREE_LIMITE_CONTAS && !getOrCreateFree(userId).isPro()) {
            throw new PlanLimitExceededException(
                    "Plano gratuito permite no máximo " + FREE_LIMITE_CONTAS +
                            " contas. Faça upgrade para o plano PRO.");
        }
    }

    /**
     * Verifica se o usuário pode criar mais transações neste mês.
     * Chamado pelo TransactionService antes de criar.
     */
    @Transactional(readOnly = true)
    public void assertPodeCriarTransacao(UUID userId, long transacoesNoMes) {
        if (transacoesNoMes >= FREE_LIMITE_TRANSACOES_MES && !getOrCreateFree(userId).isPro()) {
            throw new PlanLimitExceededException(
                    "Plano gratuito permite no máximo " + FREE_LIMITE_TRANSACOES_MES +
                            " transações por mês. Faça upgrade para o plano PRO.");
        }
    }

    /**
     * Verifica se o usuário tem plano PRO.
     * Usar para funcionalidades exclusivas PRO (Open Finance, exportação).
     */
    @Transactional(readOnly = true)
    public void assertIsPro(UUID userId, String funcionalidade) {
        if (!getOrCreateFree(userId).isPro()) {
            throw new PlanLimitExceededException(
                    funcionalidade + " é exclusivo do plano PRO.");
        }
    }

    // ── Ativação/desativação (chamado pelo webhook do Stripe) ─────────────

    /**
     * Ativa o plano PRO após pagamento confirmado.
     * Chamado pelo webhook do Stripe quando:
     * 1. payment_intent.succeeded (pagamento único)
     * 2. customer.subscription.updated (renovação de subscription)
     *
     * @param userId         ID do usuário interno
     * @param paymentIntentId ID do payment_intent (para pagamento único)
     * @param subscriptionId ID da subscription (para recorrente)
     * @param inicio         Data de início do período PRO
     * @param fim            Data de término do período PRO
     */
    @Transactional
    public void ativarPro(UUID userId, String paymentIntentId, String subscriptionId,
                          LocalDate inicio, LocalDate fim) {
        Subscription sub = getOrCreateFree(userId);
        sub.setPlano(PlanType.PRO);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setInicioPeriodo(inicio);
        sub.setFimPeriodo(fim);
        sub.setStripePaymentIntentId(paymentIntentId);
        sub.setStripeSubscriptionId(subscriptionId);
        subscriptionRepository.save(sub);

        log.info("[Subscription] PRO ativado: userId={} até={}", userId, fim);

        // Notifica o usuário
        notificationService.criarNotificacao(
                null,
                userId,
                NotificationType.SUBSCRIPTION_ACTIVATED,
                "Parabéns! Você agora é um usuário PRO até " + fim,
                null
        );
    }

    /**
     * Cancela a subscription do usuário (revoga PRO).
     * Se houver uma subscription recorrente no Stripe, cancela lá também.
     */
    @Transactional
    public void cancelarSubscricao(UUID userId) {
        Subscription sub = subscriptionRepository.findByUserId(userId)
                .orElseThrow(() ->
                ResourceNotFoundException.of("Subscription", userId)
        );

        // Se tem subscription_id no Stripe, cancela lá
        if (sub.getStripeSubscriptionId() != null && !sub.getStripeSubscriptionId().isBlank()) {
            stripeService.cancelarSubscription(sub.getStripeSubscriptionId());
        }

        // Marca como cancelada localmente
        sub.setStatus(SubscriptionStatus.CANCELLED);
        sub.setPlano(PlanType.FREE);
        subscriptionRepository.save(sub);

        log.info("[Subscription] Assinatura cancelada: userId={}", userId);

        // Notifica o usuário
        notificationService.criarNotificacao(
                null,
                userId,
                NotificationType.SUBSCRIPTION_CANCELED,
                "Sua assinatura PRO foi cancelada. Você voltou ao plano FREE.",
                null
        );
    }

    /**
     * Expira assinaturas PRO vencidas.
     * Chamado pelo SubscriptionExpirationScheduler diariamente.
     *
     * Muda status de ACTIVE para EXPIRED e plano de PRO para FREE.
     */
    @Transactional
    public void expirarAssinaturasVencidas() {
        LocalDate hoje = LocalDate.now();
        subscriptionRepository.findProExpiradas(hoje).forEach(sub -> {
            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlano(PlanType.FREE);
            subscriptionRepository.save(sub);
            log.info("[Subscription] PRO expirado: userId={}", sub.getUserId());

            // Notifica o usuário que PRO expirou
            notificationService.criarNotificacao(
                    null,
                    sub.getUserId(),
                    NotificationType.SUBSCRIPTION_EXPIRED,
                    "Sua assinatura PRO expirou. Você voltou ao plano FREE.",
                    null
            );
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Busca a assinatura do usuário. Se não existir, cria automaticamente como FREE.
     * Todo usuário começa no FREE no momento do primeiro acesso a este método.
     */
    public Subscription getOrCreateFree(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Subscription nova = Subscription.builder()
                            .userId(userId)
                            .plano(PlanType.FREE)
                            .status(SubscriptionStatus.ACTIVE)
                            .build();
                    return subscriptionRepository.save(nova);
                });
    }

    /**
     * Extrai o UUID do usuário a partir do AuthenticatedUser.
     */
    private UUID resolveUserId(AuthenticatedUser caller) {
        return caller.id();
    }
}