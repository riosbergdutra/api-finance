package com.api.finance.notification.infrastructure.adapter;

import com.api.finance.notification.application.port.out.NotificationPushMessage;
import com.api.finance.notification.application.port.out.NotificationPushPort;
import com.api.finance.notification.dto.NotificationResponse;
import com.api.finance.notification.websocket.NotificationWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Adaptador que traduz o contrato de aplicação para JSON e transporte WebSocket. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketNotificationPushAdapter implements NotificationPushPort {

    private final NotificationWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(String identityProviderUserId, NotificationPushMessage notification) {
        NotificationResponse response = new NotificationResponse(
                notification.id(),
                notification.tipo(),
                notification.titulo(),
                notification.mensagem(),
                notification.lida(),
                notification.entidadeTipo(),
                notification.entidadeId(),
                notification.criadoEm()
        );
        try {
            webSocketHandler.sendToUser(
                    identityProviderUserId,
                    objectMapper.writeValueAsString(response)
            );
        } catch (JsonProcessingException exception) {
            // Persistência já ocorreu; push é best-effort e não deve desfazer a notificação.
            log.warn("[Notification] Falha ao serializar push: {}", exception.getMessage());
        }
    }
}
