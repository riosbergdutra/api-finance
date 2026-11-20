package com.api.finance.bff.event;

import com.api.finance.notification.websocket.NotificationWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Receives revocations published by any FinanceFlow backend replica. */
@Component
@RequiredArgsConstructor
public class BffSessionRevokedRedisSubscriber implements MessageListener {

    private final NotificationWebSocketHandler webSocketHandler;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String sessionId = new String(message.getBody(), StandardCharsets.UTF_8);
        if (!sessionId.isBlank()) {
            webSocketHandler.closeConnectionsForRevokedSession(sessionId);
        }
    }
}
