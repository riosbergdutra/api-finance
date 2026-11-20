package com.api.finance.bff.event;

import com.api.finance.config.BffRedisPubSubConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Broadcasts session revocations so every backend replica closes its local sockets. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BffSessionRevocationRedisPublisher {

    private final StringRedisTemplate redis;

    @EventListener
    public void publish(BffSessionRevokedEvent event) {
        try {
            redis.convertAndSend(BffRedisPubSubConfig.SESSION_REVOKED_TOPIC, event.sessionId());
        } catch (RuntimeException unavailable) {
            // Local revocation listeners still run; remote replicas will recover
            // their stale sockets when the connections close or the nodes restart.
            log.warn("Redis Pub/Sub indisponível; revogação WebSocket remota não foi propagada.");
        }
    }
}
