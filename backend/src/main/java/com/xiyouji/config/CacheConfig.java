package com.xiyouji.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 缓存配置
 *
 * 解决问题: JPA 实体对象默认使用 JDK 序列化存储到 Redis, 会导致序列化失败
 * 解决方案: 使用 Jackson JSON 序列化, 支持复杂对象且可读性好
 *
 * 安全: 使用 BasicPolymorphicTypeValidator 显式允许应用包 + JDK 基础类型,
 * 替代不安全的 LaissezFaireSubTypeValidator, 防止 Redis 数据被污染时触发反序列化漏洞
 *
 * 分布式缓存核心概念:
 * - 多个实例共享同一个 Redis 缓存
 * - 实例1查询数据库后缓存到 Redis, 实例2直接从 Redis 读取
 * - 减少数据库压力, 提升响应速度
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** 允许反序列化的类型白名单：应用包 + JPA 集合 + JDK 基础类型 */
    private static final PolymorphicTypeValidator TYPE_VALIDATOR = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .allowIfSubType("com.xiyouji.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("java.time.")
            .allowIfSubType("org.springframework.data.domain.")
            .build();

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // JSON 序列化配置
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 安全的多态类型处理：仅允许白名单内的类型反序列化
        objectMapper.activateDefaultTyping(
                TYPE_VALIDATOR,
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // 缓存配置: Key 用 String, Value 用 JSON
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))  // 缓存有效期 30 分钟
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer))
                .disableCachingNullValues();  // 不缓存 null 值

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }
}
