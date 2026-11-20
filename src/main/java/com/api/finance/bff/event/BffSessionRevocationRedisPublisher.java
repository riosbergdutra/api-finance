package com.api.finance.bff.event;

import com.api.finance.config.BffRedisPubSubConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Broadcasts session revocations so every backend replica closes its local sockets. */
@Component
@RequiredArgsConstructor
public class BffSessionRevocationRedisPublisher {

    private final StringRedisTemplate redis;

    @EventListener
    public void publish(BffSessionRevokedEvent event) {
        redis.convertAndSend(BffRedisPubSubConfig.SESSION_REVOKED_TOPIC, event.sessionId());
    }
}
