package com.lym.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.neo4j")
public class Neo4jClientProperties {

    /**
     * Neo4j Bolt 连接地址，例如 bolt://175.178.182.172:7687
     */
    private String uri;

    private Authentication authentication = new Authentication();

    @Data
    public static class Authentication {
        private String username;
        private String password;
    }
}