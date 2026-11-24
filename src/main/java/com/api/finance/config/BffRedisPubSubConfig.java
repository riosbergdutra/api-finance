package com.api.finance.config;

import com.api.finance.bff.event.BffSessionRevokedRedisSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(
        name = "app.bff.redis-pubsub.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class BffRedisPubSubConfig {

    public static final String SESSION_REVOKED_TOPIC = "finance:bff:session-revoked";

    @Bean
    public ChannelTopic bffSessionRevokedTopic() {
        return new ChannelTopic(SESSION_REVOKED_TOPIC);
    }

    @Bean
    public RedisMessageListenerContainer bffRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            BffSessionRevokedRedisSubscriber subscriber,
            ChannelTopic bffSessionRevokedTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, bffSessionRevokedTopic);
        return container;
    }
}
