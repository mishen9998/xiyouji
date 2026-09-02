package com.xiyouji.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置
 * 仅在 app.redis.session-enabled=true 时生效
 * 使用 GenericJackson2JsonRedisSerializer 进行JSON序列化
 * 配置 ObjectMapper 激活类型信息，确保多态反序列化正确
 * 注册 JavaTimeModule 以支持 LocalDateTime 等 Java 8 日期时间类型
 */
@Configuration
@ConditionalOnProperty(name = "app.redis.session-enabled", havingValue = "true")
public class RedisConfig {

    /**
     * 配置 RedisTemplate<String, Object>
     * Key 使用 StringRedisSerializer，Value 使用 GenericJackson2JsonRedisSerializer
     * 带类型信息的JSON序列化，确保 GameSession 及其嵌套对象能正确反序列化
     *
     * @param connectionFactory Redis连接工厂
     * @return 配置好的 RedisTemplate
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 构建带类型信息的 ObjectMapper
        ObjectMapper objectMapper = new ObjectMapper();
        // 访问所有字段（包括 private），忽略 JPA 注解的影响
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 注册 JavaTimeModule 支持 LocalDateTime 等 Java 8 日期时间类型
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // 反序列化时忽略未知属性（如计算属性 isFull/getPlayerCount 等），避免失败
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        // 激活默认类型信息：非 final 类型序列化时写入 @class，反序列化时据此还原
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
