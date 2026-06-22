package com.api.finance.events;

import com.api.finance.notification.model.NotificationType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Eventos de domínio do FinanceFlow.
 *
 * PADRÃO ESCOLHIDO: ApplicationEventPublisher + @TransactionalEventListener(AFTER_COMMIT)
 *
 * POR QUE NÃO CHAMADA DIRETA (@Async no NotificationService):
 *   - Chamada direta funciona, mas acopla GoalService → NotificationService diretamente.
 *   - Se a transação principal fizer rollback APÓS o @Async já ter enviado a notificação
 *     (ex: erro ao salvar no banco), o usuário recebe notificação de algo que não aconteceu.
 *
 * POR QUE @TransactionalEventListener(AFTER_COMMIT):
 *   - A notificação SÓ é enviada quando a transação principal commitou com sucesso.
 *   - GoalService conhece apenas o evento "MetaConcluida" — não conhece NotificationService.
 *   - Adicionar um novo listener (ex: analytics, email) não toca em GoalService.
 *   - Testabilidade: basta verificar se o evento foi publicado, não se o serviço foi chamado.
 *
 * TODOS OS EVENTOS SÃO RECORDS (imutáveis) com os dados mínimos necessários.
 * O listener busca dados adicionais do banco se precisar.
 */
public final class FinanceEvents {

    private FinanceEvents() {}

    // ── Meta ──────────────────────────────────────────────────────────────

    /**
     * Publicado quando uma meta atinge o valor alvo (depositar()) ou é concluída manualmente (concluir()).
     */
    public record MetaConcluidaEvent(
            String keycloakId,
            UUID userId,
            UUID goalId,
            String nomeGoal,
            BigDecimal valorAlvo
    ) {}

    /**
     * Publicado quando um saque abre uma meta que estava concluída.
     * Pouco comum, mas garante consistência de estado.
     */
    public record MetaReabertaEvent(
            String keycloakId,
            UUID userId,
            UUID goalId,
            String nomeGoal
    ) {}

    // ── Transação ─────────────────────────────────────────────────────────

    /**
     * Publicado quando uma transação CONFIRMADA deixa o saldo de uma conta negativo.
     */
    public record ContaSaldoNegativoEvent(
            String keycloakId,
            UUID userId,
            UUID accountId,
            String nomeAccount,
            BigDecimal novoSaldo
    ) {}

    /**
     * Publicado quando uma transação é criada/atualizada com status PENDENTE.
     * Lembra o usuário de confirmar manualmente.
     */
    public record TransacaoPendenteEvent(
            String keycloakId,
            UUID userId,
            UUID transactionId,
            String descricao,
            BigDecimal valor
    ) {}

    // ── Assinatura ────────────────────────────────────────────────────────

    /**
     * Publicado quando o plano PRO é ativado (via webhook do Mercado Pago).
     */
    public record ProAtivadoEvent(
            String keycloakId,
            UUID userId,
            java.time.LocalDate fimPeriodo
    ) {}

    /**
     * Publicado quando uma assinatura PRO expira e é rebaixada para FREE.
     */
    public record AssinaturaExpiradaEvent(
            String keycloakId,
            UUID userId
    ) {}
}
