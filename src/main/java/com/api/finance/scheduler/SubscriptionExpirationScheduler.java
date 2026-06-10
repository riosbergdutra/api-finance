package com.api.finance.scheduler;

import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.service.NotificationService;
import com.api.finance.subscription.model.Subscription;
import com.api.finance.subscription.repository.SubscriptionRepository;
import com.api.finance.subscription.service.SubscriptionService;
import com.api.finance.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Job agendado: verifica assinaturas PRO que expiram em breve ou já expiraram.
 *
 * CRON: todo dia à meia-noite (horário de Brasília)
 *
 * Ações:
 * 1. Notifica usuários PRO cuja assinatura vence em 7 dias
 * 2. Expira assinaturas PRO vencidas (rebaixa para FREE)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpirationScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Sao_Paulo")
    public void processarExpiracoes() {
        log.info("[Scheduler] Processando expirações de assinatura");

        LocalDate hoje = LocalDate.now();
        LocalDate em7Dias = hoje.plusDays(7);

        // 1. Notifica PRO expirando em 7 dias
        List<Subscription> expirando = subscriptionRepository.findProExpirandoAte(hoje, em7Dias);
        for (Subscription sub : expirando) {
            long dias = sub.diasParaExpirar();
            userRepository.findById(sub.getUserId()).ifPresent(user -> {
                notificationService.criarNotificacao(
                        user.getKeycloakId().toString(),
                        user.getId(),
                        NotificationType.ASSINATURA_EXPIRANDO,
                        "Assinatura PRO expirando",
                        String.format("Sua assinatura PRO vence em %d dia(s). Renove para continuar com acesso ilimitado.", dias)
                );
            });
        }

        // 2. Expira e rebaixa para FREE assinaturas vencidas
        subscriptionService.expirarAssinaturasVencidas();

        log.info("[Scheduler] {} assinaturas notificadas, expirações processadas", expirando.size());
    }
}
