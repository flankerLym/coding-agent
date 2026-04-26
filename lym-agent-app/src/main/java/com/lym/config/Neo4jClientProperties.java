package com.lym.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.datasource.neo4j")
public class Neo4jClientProperties {

    private String uri;

    private Authentication authentication = new Authentication();

    @Data
    public static class Authentication {
        private String username;
        private String password;
    }
}