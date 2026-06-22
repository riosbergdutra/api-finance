package com.api.finance.notification;

import com.api.finance.events.FinanceEvents;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

/**
 * Listener central de eventos de domínio → notificações.
 *
 * DESIGN:
 * - @TransactionalEventListener(AFTER_COMMIT): só executa APÓS a transação principal
 *   ter commitado com sucesso. Garante que o usuário nunca recebe notificação de
 *   algo que ainda vai sofrer rollback.
 *
 * - @Async: executa em thread separada (Virtual Thread com @EnableAsync), não bloqueando
 *   a resposta HTTP enquanto persiste a notificação e tenta o WebSocket push.
 *
 * - Um único listener por evento: fácil de testar, fácil de estender. Para adicionar
 *   analytics ou email, basta criar outro @Component com @TransactionalEventListener
 *   para o mesmo evento — sem tocar em nenhum service de domínio.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    // ── Meta ──────────────────────────────────────────────────────────────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMetaConcluida(FinanceEvents.MetaConcluidaEvent event) {
        log.debug("[Event] MetaConcluida: goalId={} user={}", event.goalId(), event.userId());
        notificationService.criarNotificacao(
                event.keycloakId(),
                event.userId(),
                NotificationType.META_CONCLUIDA,
                "Meta concluída: " + event.nomeGoal(),
                String.format("Parabéns! Você atingiu sua meta \"%s\" de R$ %.2f.",
                        event.nomeGoal(), event.valorAlvo()),
                "GOAL",
                event.goalId()
        );
    }

    // ── Transação ─────────────────────────────────────────────────────────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContaSaldoNegativo(FinanceEvents.ContaSaldoNegativoEvent event) {
        log.debug("[Event] ContaSaldoNegativo: accountId={} saldo={}", event.accountId(), event.novoSaldo());
        notificationService.criarNotificacao(
                event.keycloakId(),
                event.userId(),
                NotificationType.CONTA_SALDO_NEGATIVO,
                "Saldo negativo: " + event.nomeAccount(),
                String.format("Atenção! O saldo da conta \"%s\" ficou negativo: R$ %.2f.",
                        event.nomeAccount(), event.novoSaldo()),
                "ACCOUNT",
                event.accountId()
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransacaoPendente(FinanceEvents.TransacaoPendenteEvent event) {
        log.debug("[Event] TransacaoPendente: transactionId={}", event.transactionId());
        notificationService.criarNotificacao(
                event.keycloakId(),
                event.userId(),
                NotificationType.TRANSACAO_PENDENTE,
                "Transação pendente de confirmação",
                String.format("A transação \"%s\" de R$ %.2f está pendente. Confirme quando realizada.",
                        event.descricao() != null ? event.descricao() : "sem descrição",
                        event.valor()),
                "TRANSACTION",
                event.transactionId()
        );
    }

    // ── Assinatura ────────────────────────────────────────────────────────

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProAtivado(FinanceEvents.ProAtivadoEvent event) {
        log.debug("[Event] ProAtivado: userId={} até={}", event.userId(), event.fimPeriodo());
        notificationService.criarNotificacao(
                event.keycloakId(),
                event.userId(),
                NotificationType.ASSINATURA_EXPIRANDO, // reutiliza o mais próximo disponível
                "Plano PRO ativado!",
                String.format("Seu plano PRO está ativo até %s. Aproveite todos os recursos ilimitados!",
                        event.fimPeriodo().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")))
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssinaturaExpirada(FinanceEvents.AssinaturaExpiradaEvent event) {
        log.debug("[Event] AssinaturaExpirada: userId={}", event.userId());
        notificationService.criarNotificacao(
                event.keycloakId(),
                event.userId(),
                NotificationType.ASSINATURA_EXPIRADA,
                "Plano PRO expirado",
                "Seu plano PRO expirou. Renove para continuar com acesso ilimitado a todas as funcionalidades."
        );
    }
}