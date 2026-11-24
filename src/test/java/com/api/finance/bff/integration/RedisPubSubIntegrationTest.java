package com.api.finance.bff.integration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisPubSubIntegrationTest {

    private static final String TOPIC = "finance:bff:session-revoked-test";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private static LettuceConnectionFactory publisherConnection;
    private static LettuceConnectionFactory subscriberConnection;
    private static StringRedisTemplate publisher;
    private static RedisMessageListenerContainer listenerContainer;

    private static CountDownLatch received;
    private static AtomicReference<String> receivedMessage;

    @BeforeAll
    static void setUpRedisClients() throws Exception {
        publisherConnection = connectionFactory();
        subscriberConnection = connectionFactory();
        publisher = new StringRedisTemplate(publisherConnection);
        publisher.afterPropertiesSet();

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(subscriberConnection);

        received = new CountDownLatch(1);
        receivedMessage = new AtomicReference<>();
        listenerContainer.addMessageListener((MessageListener) (Message message, byte[] pattern) -> {
            receivedMessage.set(new String(message.getBody(), StandardCharsets.UTF_8));
            received.countDown();
        }, new ChannelTopic(TOPIC));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (received.getCount() > 0 && System.nanoTime() < deadline) {
            publisher.convertAndSend(TOPIC, "subscription-revoked");
            received.await(100, TimeUnit.MILLISECONDS);
        }
        assertThat(received.getCount()).isZero();
        assertThat(receivedMessage.get()).isEqualTo("subscription-revoked");
    }

    @AfterAll
    static void tearDownRedisClients() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
        if (publisherConnection != null) {
            publisherConnection.destroy();
        }
        if (subscriberConnection != null) {
            subscriberConnection.destroy();
        }
    }

    @Test
    void publishesSessionRevocationAcrossIndependentRedisConnections() throws Exception {
        received = new CountDownLatch(1);
        receivedMessage.set(null);
        String sessionId = "123e4567-e89b-12d3-a456-426614174000";

        Long subscribers = publisher.convertAndSend(TOPIC, sessionId);

        assertThat(subscribers).isGreaterThanOrEqualTo(1L);
        assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedMessage.get()).isEqualTo(sessionId);
    }

    private static LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                REDIS.getHost(),
                REDIS.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }
}
