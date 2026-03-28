package com.lym.config;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class AiAgentConfig {
    @Bean("vectorStore")
    public PgVectorStore pgVectorStore(@Value("${spring.ai.zhipuai.api-key}") String apiKey,
                                       ZhiPuAiEmbeddingModel embeddingModel,
                                       @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate) {

        // 构建 PgVector 向量库
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("vector_store_openai")
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
