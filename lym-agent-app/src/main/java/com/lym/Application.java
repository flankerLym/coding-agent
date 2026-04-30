package com.lym;

import com.lym.config.AiAgentAutoConfigProperties;
import com.lym.config.Neo4jClientProperties;
import com.lym.config.RedisClientProperties;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@Configurable
@EnableTransactionManagement
@EnableConfigurationProperties({
        AiAgentAutoConfigProperties.class,
        Neo4jClientProperties.class,
        RedisClientProperties.class
})
public class Application {

    public static void main(String[] args){
        SpringApplication.run(Application.class);
    }

}
