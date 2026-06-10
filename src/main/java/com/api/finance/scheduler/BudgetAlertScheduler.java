package com.api.finance.scheduler;

import com.api.finance.budget.model.Budget;
import com.api.finance.budget.service.BudgetService;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.service.NotificationService;
import com.api.finance.user.model.User;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job agendado: verifica alertas de orçamento diariamente.
 *
 * DIFERENÇA DO ORIGINAL:
 * A versão anterior chamava userRepository.findAll() — carregava TODOS os usuários
 * em memória de uma vez. Com 10.000 usuários, isso seria ~10MB só de objetos User,
 * fora o GC pressure.
 *
 * SOLUÇÃO: paginação com PageRequest.
 * Processa N usuários por vez (PAGE_SIZE = 100). O loop termina quando não há
 * mais páginas. A query usa OFFSET/LIMIT no banco — muito mais eficiente.
 *
 * PARA ESCALAR AINDA MAIS (múltiplas instâncias):
 * Se você rodar mais de uma instância do app, múltiplos @Scheduled vão disparar
 * ao mesmo tempo. Adicione Quartz com JobStore no banco para garantir que apenas
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BudgetAlertScheduler {

    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final BudgetService budgetService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *", zone = "America/Sao_Paulo")
    public void verificarAlertasOrcamento() {
        log.info("[Scheduler] Iniciando verificação de alertas de orçamento (paginado)");
        int totalAlertas = 0;
        int totalUsuariosProcessados = 0;
        int pagina = 0;

        Page<User> paginaAtual;

        do {
            paginaAtual = userRepository.findAll(PageRequest.of(pagina, PAGE_SIZE));
            List<User> usuarios = paginaAtual.getContent();

            for (User user : usuarios) {
                try {
                    totalAlertas += processarUsuario(user);
                    totalUsuariosProcessados++;
                } catch (Exception e) {
                    log.error("[Scheduler] Erro ao verificar alertas para userId={}: {}",
                            user.getId(), e.getMessage());
                }
            }

            pagina++;

        } while (paginaAtual.hasNext());

        log.info("[Scheduler] Verificação concluída — {} usuários processados, {} alertas disparados",
                totalUsuariosProcessados, totalAlertas);
    }

    private int processarUsuario(User user) {
        List<Budget> alertas = budgetService.verificarAlertas(user.getId());
        int count = 0;

        for (Budget budget : alertas) {
            String categoria = budget.getCategory() != null
                    ? budget.getCategory().getNome()
                    : "Geral";
            double pct = budget.getPercentualGasto();

            String titulo = pct >= 100
                    ? "Orçamento estourado: " + categoria
                    : "Alerta de orçamento: " + categoria;

            String mensagem = pct >= 100
                    ? String.format("Você ultrapassou o limite de R$ %.2f em %s.",
                    budget.getValorLimite(), categoria)
                    : String.format("Você atingiu %.0f%% do orçamento de %s (limite: R$ %.2f).",
                    pct, categoria, budget.getValorLimite());

            notificationService.criarNotificacao(
                    user.getKeycloakId().toString(),
                    user.getId(),
                    pct >= 100 ? NotificationType.ORCAMENTO_ESTOURADO : NotificationType.ORCAMENTO_PROXIMO,
                    titulo,
                    mensagem,
                    "BUDGET",
                    budget.getId()
            );
            count++;
        }

        return count;
    }
}