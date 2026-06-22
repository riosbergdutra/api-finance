package com.api.finance.scheduler;

import com.api.finance.goal.model.Goal;
import com.api.finance.goal.repository.GoalRepository;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * FIX: substituído userRepository.findAll() por paginação com PageRequest,
 * igual ao padrão adotado no BudgetAlertScheduler.
 *
 * CRON: todo dia às 8h (horário de Brasília)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoalDeadlineScheduler {

    private static final int PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final GoalRepository goalRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    public void verificarPrazosDeMetAs() {
        log.info("[Scheduler] Verificando prazos de metas");
        LocalDate hoje  = LocalDate.now();
        LocalDate amanha = hoje.plusDays(1);
        int total = 0;
        int pagina = 0;

        Page<User> paginaAtual;
        do {
            paginaAtual = userRepository.findAll(PageRequest.of(pagina, PAGE_SIZE));

            for (User user : paginaAtual.getContent()) {
                try {
                    List<Goal> metas = goalRepository.findByUserIdAndConcluidaFalse(user.getId());
                    for (Goal meta : metas) {
                        if (meta.getDataAlvo() == null) continue;

                        long diasRestantes = ChronoUnit.DAYS.between(hoje, meta.getDataAlvo());

                        if (diasRestantes == 7 || meta.getDataAlvo().equals(amanha)) {
                            String titulo = diasRestantes <= 1
                                    ? "Meta vence amanhã: " + meta.getNome()
                                    : "Meta vence em 7 dias: " + meta.getNome();

                            String mensagem = String.format(
                                    "Sua meta \"%s\" vence em %d dia(s). Você juntou R$ %.2f de R$ %.2f (%.0f%%).",
                                    meta.getNome(),
                                    diasRestantes,
                                    meta.getValorAtual(),
                                    meta.getValorAlvo(),
                                    meta.getPercentualConcluido()
                            );

                            notificationService.criarNotificacao(
                                    user.getKeycloakId().toString(),
                                    user.getId(),
                                    NotificationType.META_PRAZO,
                                    titulo,
                                    mensagem,
                                    "GOAL",
                                    meta.getId()
                            );
                            total++;
                        }
                    }
                } catch (Exception e) {
                    log.error("[Scheduler] Erro ao verificar metas userId={}: {}", user.getId(), e.getMessage());
                }
            }
            pagina++;
        } while (paginaAtual.hasNext());

        log.info("[Scheduler] {} notificações de prazo de meta disparadas", total);
    }
}