package com.lym.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.core.Neo4jClient;

@Configuration
@EnableConfigurationProperties(Neo4jClientProperties.class)
public class Neo4jClientConfig {

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(Neo4jClientProperties properties) {
        return GraphDatabase.driver(
                properties.getUri(),
                AuthTokens.basic(
                        properties.getAuthentication().getUsername(),
                        properties.getAuthentication().getPassword()
                )
        );
    }

    @Bean
    public Neo4jClient neo4jClient(Driver neo4jDriver) {
        return Neo4jClient.create(neo4jDriver);
    }
}