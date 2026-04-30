package com.lym.config;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 客户端 Bean 配置。
 */
@Configuration
public class RedisClientConfig {

    @Bean("redisConnectionFactory")
    public LettuceConnectionFactory redisConnectionFactory(RedisClientProperties properties) {
        RedisStandaloneConfiguration standaloneConfiguration = new RedisStandaloneConfiguration();
        standaloneConfiguration.setHostName(properties.getHost());
        standaloneConfiguration.setPort(properties.getPort());
        standaloneConfiguration.setDatabase(properties.getDatabase());

        if (StringUtils.isNotBlank(properties.getUsername())) {
            standaloneConfiguration.setUsername(properties.getUsername());
        }
        if (StringUtils.isNotBlank(properties.getPassword())) {
            standaloneConfiguration.setPassword(RedisPassword.of(properties.getPassword()));
        }

        LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration.builder()
                .commandTimeout(properties.getTimeout())
                .shutdownTimeout(Duration.ofMillis(100))
                .build();

        return new LettuceConnectionFactory(standaloneConfiguration, clientConfiguration);
    }

    @Bean("stringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(redisConnectionFactory);
        stringRedisTemplate.afterPropertiesSet();
        return stringRedisTemplate;
    }

    @Bean("redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();

        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);
        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);
        redisTemplate.setDefaultSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

}
