package com.api.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.api.finance.notification.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;

/**
 * Configura o endpoint WebSocket para notificações em tempo real.
 *
 * O Angular conecta em: ws://localhost:8080/ws/notifications?token=<access_token>
 * O handler valida o token JWT antes de aceitar a conexão.
 *
 * Por que WebSocket puro (não STOMP/SockJS)?
 * - Mais simples para o caso de uso de notificações unidirecionais (server → client)
 * - Sem overhead de STOMP para um canal simples de push
 * - STOMP/SockJS faz sentido quando há pub/sub bidirecional complexo
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationWebSocketHandler notificationHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(notificationHandler, "/ws/notifications")
            // Permite conexão do Angular em dev (localhost:4200) e prod (domínio configurado)
            .setAllowedOriginPatterns("http://localhost:4200", "https://*.seudominio.com.br");
    }
}
