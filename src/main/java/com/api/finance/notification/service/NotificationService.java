package com.api.finance.notification.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.notification.dto.NotificationResponse;
import com.api.finance.notification.model.Notification;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.notification.application.port.out.UserIdentityPort;
import com.api.finance.notification.application.port.out.NotificationPushPort;
import com.api.finance.notification.application.port.out.NotificationPushMessage;
import com.api.finance.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Serviço de notificações.
 *
 * FLUXO COMPLETO:
 * 1. Outro serviço (BudgetScheduler, GoalService, etc.) chama criarNotificacao()
 * 2. A notificação é persistida no banco (sempre — mesmo se usuário offline)
 * 3. @Async: tenta enviar via WebSocket se o usuário tiver sessão aberta
 * 4. Se offline: notificação fica no banco, Angular busca no próximo login
 *
 * Isso garante que nenhuma notificação é perdida — WebSocket é um "bonus"
 * de entrega imediata, não o único canal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserIdentityPort userIdentityPort;
    private final NotificationPushPort notificationPushPort;

    // ── Leitura ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listar(Pageable pageable, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return notificationRepository
                .findByUserIdOrderByLidaAscCriadoEmDesc(userId, pageable)
                .map(NotificationResponse::de);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listarNaoLidas(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return notificationRepository
                .findByUserIdAndLidaFalseOrderByCriadoEmDesc(userId)
                .stream().map(NotificationResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return notificationRepository.countByUserIdAndLidaFalse(userId);
    }

    // ── Escrita ─────────────────────────────────────────────────────────

    @Transactional
    public void marcarComoLida(UUID id, AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        Notification notif = notificationRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", id));
        notif.setLida(true);
        notificationRepository.save(notif);
    }

    @Transactional
    public int marcarTodasComoLidas(AuthenticatedUser caller) {
        UUID userId = resolveUserId(caller);
        return notificationRepository.marcarTodasComoLidas(userId);
    }

    // ── Criação interna + push WebSocket ─────────────────────────────────

    /**
     * Persiste a notificação e tenta entrega em tempo real via WebSocket.
     *
     * @Async garante que este método não bloqueia o fluxo principal (ex: criar transação).
     * Se o usuário estiver offline, a notificação fica no banco e é entregue no próximo login.
     *
     * @param keycloakId  ID do Keycloak (necessário para o WebSocket lookup)
     * @param userId      UUID interno do banco (necessário para persistência)
     */
    @Async
    @Transactional
    public void criarNotificacao(String keycloakId, UUID userId, NotificationType tipo,
                                 String titulo, String mensagem,
                                 String entidadeTipo, UUID entidadeId) {
        persistirNotificacao(keycloakId, userId, tipo, titulo, mensagem, entidadeTipo, entidadeId);
    }

    /** Atalho sem entidade relacionada; a chamada externa também passa pelo proxy @Async. */
    @Async
    @Transactional
    public void criarNotificacao(String keycloakId, UUID userId,
                                 NotificationType tipo, String titulo, String mensagem) {
        persistirNotificacao(keycloakId, userId, tipo, titulo, mensagem, null, null);
    }

    private void persistirNotificacao(String keycloakId, UUID userId, NotificationType tipo,
                                      String titulo, String mensagem,
                                      String entidadeTipo, UUID entidadeId) {
        Notification notif = Notification.builder()
                .userId(userId)
                .tipo(tipo)
                .titulo(titulo)
                .mensagem(mensagem)
                .entidadeTipo(entidadeTipo)
                .entidadeId(entidadeId)
                .build();

        notificationRepository.save(notif);
        log.debug("[Notification] Persistida: tipo={} userId={}", tipo, userId);

        // Entrega em tempo real é uma porta de saída e não controla a persistência.
        notificationPushPort.publish(keycloakId, new NotificationPushMessage(
                notif.getId(), notif.getTipo(), notif.getTitulo(), notif.getMensagem(),
                notif.isLida(), notif.getEntidadeTipo(), notif.getEntidadeId(), notif.getCriadoEm()
        ));
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userIdentityPort.findInternalUserId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}