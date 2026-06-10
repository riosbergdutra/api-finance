package com.api.finance.notification.service;

import com.api.finance.config.AuthenticatedUser;
import com.api.finance.notification.dto.NotificationResponse;
import com.api.finance.notification.model.Notification;
import com.api.finance.notification.model.NotificationType;
import com.api.finance.notification.repository.NotificationRepository;
import com.api.finance.shared.exception.ResourceNotFoundException;
import com.api.finance.user.repository.UserRepository;
import com.api.finance.notification.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final UserRepository userRepository;
    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

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
        // 1. Persiste sempre — independente do canal de entrega
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

        // 2. Push em tempo real via WebSocket — best-effort
        enviarViaPush(keycloakId, NotificationResponse.de(notif));
    }

    /** Atalho sem entidade relacionada */
    @Async
    public void criarNotificacao(String keycloakId, UUID userId,
                                 NotificationType tipo, String titulo, String mensagem) {
        criarNotificacao(keycloakId, userId, tipo, titulo, mensagem, null, null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void enviarViaPush(String keycloakId, NotificationResponse notif) {
        try {
            String json = objectMapper.writeValueAsString(notif);
            webSocketHandler.sendToUser(keycloakId, json);
        } catch (JsonProcessingException e) {
            log.warn("[Notification] Falha ao serializar para WebSocket: {}", e.getMessage());
        }
    }

    private UUID resolveUserId(AuthenticatedUser caller) {
        return userRepository.findIdByKeycloakId(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));
    }
}