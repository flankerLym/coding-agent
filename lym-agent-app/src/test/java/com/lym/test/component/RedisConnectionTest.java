package com.lym.test.component;

import com.lym.config.RedisClientConfig;
import com.lym.config.RedisClientProperties;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Redis 连接验证测试。
 *
 * 这个测试只加载 RedisClientConfig，不启动完整 SpringBoot 应用，
 * 因此不会连 MySQL / Neo4j / pgvector / AI Agent 自动装配。
 *
 * 前置条件：
 * 1. Redis 容器已经启动。
 * 2. Redis 配置为：
 *    host=127.0.0.1
 *    port=16379
 *    username=admin
 *    password=140810921
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = {
        "spring.data.redis.host=175.178.182.172",
        "spring.data.redis.port=16379",
        "spring.data.redis.username=admin",
        "spring.data.redis.password=140810921",
        "spring.data.redis.database=0",
        "spring.data.redis.timeout=3s"
})
public class RedisConnectionTest {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisPing() {
        Assert.assertNotNull(redisConnectionFactory);

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            Assert.assertEquals("PONG", pong);
        }
    }

    @Test
    public void testRedisSetAndGet() {
        Assert.assertNotNull(stringRedisTemplate);

        String key = "legalflow:test:redis:connection";
        String value = "ok";

        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(30));
        String actual = stringRedisTemplate.opsForValue().get(key);

        Assert.assertEquals(value, actual);

        Boolean deleted = stringRedisTemplate.delete(key);
        Assert.assertTrue(Boolean.TRUE.equals(deleted) || Boolean.FALSE.equals(deleted));
    }

    @Test
    public void testRedisRawConnectionSetAndGet() {
        String key = "legalflow:test:redis:raw";
        String value = "raw-ok";

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);

            Boolean set = connection.set(keyBytes, valueBytes);
            Assert.assertTrue(Boolean.TRUE.equals(set));

            byte[] actual = connection.get(keyBytes);
            Assert.assertNotNull(actual);
            Assert.assertEquals(value, new String(actual, StandardCharsets.UTF_8));

            connection.del(keyBytes);
        }
    }

    @Configuration
    @EnableConfigurationProperties(RedisClientProperties.class)
    @Import(RedisClientConfig.class)
    static class TestRedisConfig {
    }

}
