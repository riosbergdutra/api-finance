package com.api.finance.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.api.finance.notification.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

/**
 * Configura o endpoint WebSocket para notificações em tempo real.
 *
 * O Angular conecta em /ws/notifications; o navegador envia o cookie HttpOnly.
 * O handler valida a sessão BFF antes de aceitar a conexão.
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

    @Value("${app.cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry
            .addHandler(notificationHandler, "/ws/notifications")
            .setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(origin -> !origin.isBlank())
                    .toArray(String[]::new));
    }
}
