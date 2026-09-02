package com.xiyouji.config;

import com.xiyouji.service.room.RedisRoomEventPublisher;
import com.xiyouji.service.room.RedisRoomEventSubscriber;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Redis Pub/Sub wiring for cross-instance room and battle notifications. */
@Configuration
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic roomEventsTopic() {
        return new ChannelTopic(RedisRoomEventPublisher.CHANNEL);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisRoomEventSubscriber subscriber,
            ChannelTopic roomEventsTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, roomEventsTopic);
        return container;
    }
}
