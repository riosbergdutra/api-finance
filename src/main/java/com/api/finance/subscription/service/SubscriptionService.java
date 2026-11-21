package com.api.finance.subscription.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.events.FinanceEvents;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.subscription.dto.SubscriptionResponse;
import com.api.finance.subscription.application.port.in.SubscriptionAccountDeletionPort;
import com.api.finance.subscription.exception.PlanLimitExceededException;
import com.api.finance.subscription.model.PlanType;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.model.SubscriptionStatus;
import com.api.finance.subscription.repository.SubscriptionRepository;
import com.api.finance.user.application.port.UserDirectoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.Optional;

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
public class SubscriptionService implements SubscriptionAccountDeletionPort {

    private static final int FREE_LIMITE_CONTAS = 3;
    private static final int FREE_LIMITE_TRANSACOES_MES = 100;

    private final SubscriptionRepository subscriptionRepository;
    private final UserDirectoryPort userDirectory;
    private final StripeService stripeService;
    private final ApplicationEventPublisher eventPublisher;  // FIX: publica eventos de domínio, seguindo o mesmo padrão usado em TransactionService/GoalService

    // ── Consulta ─────────────────────────────────────────────────────────

    @Transactional
    public SubscriptionResponse getMeuPlano(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Subscription sub = getOrCreateFree(userId);
        return SubscriptionResponse.de(sub);
    }

    // ── Verificação de limites ────────────────────────────────────────────

    /**
     * Serializa operações que consomem cotas FREE do mesmo usuário.
     * Deve ser chamado antes de contar recursos e mantido até o commit da criação.
     */
    @Transactional
    public void lockSubscriptionForQuotaCheck(UUID userId) {
        subscriptionRepository.insertFreeIfAbsent(UUID.randomUUID(), userId);
        subscriptionRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Não foi possível bloquear a assinatura para verificar limites."));
    }


    /**
     * Verifica se o usuário pode criar mais uma conta.
     * Lança PlanLimitExceededException (402) se FREE e já tem 3 contas.
     * Chamado pelo AccountService antes de criar.
     */
    @Transactional(readOnly = true)
    public void assertPodeCriarConta(UUID userId, long contasAtuais) {
        if (contasAtuais >= FREE_LIMITE_CONTAS && !isPro(userId)) {
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
        if (transacoesNoMes >= FREE_LIMITE_TRANSACOES_MES && !isPro(userId)) {
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
        if (!isPro(userId)) {
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

        // FIX: antes chamava notificationService.criarNotificacao(...) passando o título
        // como se fosse a assinatura de 5 argumentos (keycloakId, userId, tipo, titulo, mensagem),
        // mas só 1 string era passada — a mensagem real da notificação ficava null.
        // Agora segue o mesmo padrão de evento usado em TransactionService/GoalService:
        // o NotificationEventListener já trata ProAtivadoEvent corretamente.
        String keycloakId = resolveKeycloakId(userId);
        eventPublisher.publishEvent(new FinanceEvents.ProAtivadoEvent(keycloakId, userId, fim));
    }

    /**
     * Cancels recurring billing before a user's local data and identity are erased.
     * Failure is propagated so account deletion does not silently leave paid billing active.
     */
    @Override
    @Transactional
    public void cancelarParaExclusao(UUID userId) {
        subscriptionRepository.findByUserIdForUpdate(userId).ifPresent(sub -> {
            String stripeSubscriptionId = sub.getStripeSubscriptionId();
            if (stripeSubscriptionId != null && !stripeSubscriptionId.isBlank()
                    && sub.getStatus() != SubscriptionStatus.CANCELLED
                    && sub.getStatus() != SubscriptionStatus.EXPIRED) {
                stripeService.cancelarSubscription(stripeSubscriptionId);
            }
            sub.setPlano(PlanType.FREE);
            sub.setStatus(SubscriptionStatus.CANCELLED);
            subscriptionRepository.save(sub);
        });
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

        // FIX: mesma correção — título e mensagem agora são passados como argumentos
        // separados, e o keycloakId real é resolvido (antes era passado null, então
        // a notificação nunca chegava via WebSocket, só ficava persistida).
        String keycloakId = resolveKeycloakId(userId);
        eventPublisher.publishEvent(new FinanceEvents.AssinaturaCanceladaEvent(keycloakId, userId));
    }

    /**
     * Applies recurring Stripe subscription events in provider-created order.
     * A row lock serializes concurrent webhook deliveries for the same subscription.
     */
    @Transactional
    public boolean aplicarEventoStripeSubscription(
            String stripeSubscriptionId,
            UUID metadataUserId,
            String stripeStatus,
            LocalDate periodEnd,
            long eventCreated,
            String eventId) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()
                || stripeStatus == null || stripeStatus.isBlank()
                || eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Dados do evento Stripe incompletos.");
        }

        Optional<Subscription> existing = subscriptionRepository
                .findByStripeSubscriptionIdForUpdate(stripeSubscriptionId);
        Subscription sub;
        if (existing.isPresent()) {
            sub = existing.get();
        } else {
            if (metadataUserId == null) {
                // An unknown provider subscription cannot be safely associated
                // with a local user. Acknowledge it instead of retrying forever.
                log.warn("[Stripe] Evento de subscription desconhecida sem metadata de usuário; nenhum entitlement alterado.");
                return false;
            }
            if (userDirectory.findIdentityProviderId(metadataUserId).isEmpty()) {
                log.warn("[Stripe] Subscription recebida para usuário local inexistente; nenhum registro órfão será criado.");
                return false;
            }
            subscriptionRepository.insertFreeIfAbsent(UUID.randomUUID(), metadataUserId);
            sub = subscriptionRepository.findByUserIdForUpdate(metadataUserId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Não foi possível bloquear a assinatura local para aplicar o evento Stripe."));
        }

        if (metadataUserId != null && !metadataUserId.equals(sub.getUserId())) {
            throw new IllegalStateException("Metadata do Stripe não corresponde ao proprietário da assinatura.");
        }
        if (sub.getStripeSubscriptionId() != null
                && !stripeSubscriptionId.equals(sub.getStripeSubscriptionId())) {
            throw new IllegalStateException("A assinatura local já está vinculada a outro ID Stripe.");
        }

        Long previousCreated = sub.getStripeLastEventCreated();
        if (previousCreated != null && eventCreated < previousCreated) {
            log.info("[Stripe] Evento antigo ignorado para subscription; eventId={} type/status={}",
                    eventId, stripeStatus);
            return false;
        }
        // Stripe timestamps have one-second precision. Never let an active
        // update from the same second resurrect a subscription already marked
        // canceled by another event from that second.
        if (previousCreated != null && eventCreated == previousCreated
                && sub.getStatus() == SubscriptionStatus.CANCELLED
                && "active".equalsIgnoreCase(stripeStatus)) {
            return false;
        }

        boolean wasPro = sub.isPro();
        sub.setStripeSubscriptionId(stripeSubscriptionId);
        sub.setStripeLastEventCreated(eventCreated);
        sub.setStripeLastEventId(eventId);

        if ("active".equalsIgnoreCase(stripeStatus)) {
            if (periodEnd == null) {
                throw new IllegalStateException(
                        "Stripe não informou o fim do período da assinatura " + stripeSubscriptionId + ".");
            }
            sub.setPlano(PlanType.PRO);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setInicioPeriodo(LocalDate.now());
            sub.setFimPeriodo(periodEnd);
        } else {
            sub.setPlano(PlanType.FREE);
            if ("canceled".equalsIgnoreCase(stripeStatus)) {
                sub.setStatus(SubscriptionStatus.CANCELLED);
            } else if ("incomplete_expired".equalsIgnoreCase(stripeStatus)) {
                sub.setStatus(SubscriptionStatus.EXPIRED);
            } else {
                // past_due, unpaid, incomplete, paused and unknown states do
                // not grant paid entitlements; Stripe can later send a newer active event.
                sub.setStatus(SubscriptionStatus.PENDING_PAYMENT);
            }
        }

        subscriptionRepository.save(sub);
        if (wasPro && !sub.isPro()) {
            String keycloakId = resolveKeycloakId(sub.getUserId());
            eventPublisher.publishEvent(
                    new FinanceEvents.AssinaturaExpiradaEvent(keycloakId, sub.getUserId()));
        } else if (!wasPro && sub.isPro()) {
            String keycloakId = resolveKeycloakId(sub.getUserId());
            eventPublisher.publishEvent(
                    new FinanceEvents.ProAtivadoEvent(keycloakId, sub.getUserId(), sub.getFimPeriodo()));
        }
        return true;
    }

    /** Aplica o evento de cancelamento à assinatura Stripe exata, sem afetar outras contas. */
    @Transactional
    public void expirarPorStripeSubscriptionId(String stripeSubscriptionId) {
        if (stripeSubscriptionId == null || stripeSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("Stripe subscription ID é obrigatório.");
        }

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(sub -> {
            if (sub.getPlano() != PlanType.PRO || sub.getStatus() != SubscriptionStatus.ACTIVE) {
                return;
            }

            sub.setStatus(SubscriptionStatus.EXPIRED);
            sub.setPlano(PlanType.FREE);
            subscriptionRepository.save(sub);

            String keycloakId = resolveKeycloakId(sub.getUserId());
            eventPublisher.publishEvent(new FinanceEvents.AssinaturaExpiradaEvent(keycloakId, sub.getUserId()));
            log.info("[Subscription] Assinatura Stripe expirada pelo webhook: userId={}", sub.getUserId());
        });
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

            // FIX: publica o evento já tratado pelo NotificationEventListener
            // (antes chamava criarNotificacao com argumentos errados e keycloakId=null).
            String keycloakId = resolveKeycloakId(sub.getUserId());
            eventPublisher.publishEvent(new FinanceEvents.AssinaturaExpiradaEvent(keycloakId, sub.getUserId()));
        });
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Busca a assinatura do usuário. Se não existir, cria automaticamente como FREE.
     * Todo usuário começa no FREE no momento do primeiro acesso a este método.
     */
    public Subscription getOrCreateFree(UUID userId) {
        subscriptionRepository.insertFreeIfAbsent(UUID.randomUUID(), userId);
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Não foi possível criar ou recuperar a assinatura FREE do usuário."));
    }

    /** Consultas de autorização são somente leitura: ausência de registro significa FREE. */
    private boolean isPro(UUID userId) {
        return subscriptionRepository.findByUserId(userId)
                .map(Subscription::isPro)
                .orElse(false);
    }

    /**
     * Extrai o UUID do usuário a partir do AuthenticatedUser.
     */
    private UUID resolveUserId(AuthenticatedUser caller) {
        return userDirectory.findInternalUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }

    /**
     * FIX: resolve o keycloakId (necessário para o push via WebSocket) a partir
     * do userId interno. Sem isso, os métodos de ativação/cancelamento/expiração
     * não tinham como popular esse campo e passavam null — a notificação
     * acabava só persistida no banco, nunca chegando em tempo real ao usuário.
     */
    private String resolveKeycloakId(UUID userId) {
        return userDirectory.findIdentityProviderId(userId)
                .map(Object::toString)
                .orElse(null);
    }
}