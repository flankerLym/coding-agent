package com.lym.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Redis 客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisClientProperties {

    private String host = "127.0.0.1";

    private Integer port = 6379;

    private String username;

    private String password;

    private Integer database = 0;

    private Duration timeout = Duration.ofSeconds(3);

}
