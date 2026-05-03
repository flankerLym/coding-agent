package com.lym.config;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class PgVectorConfig {
    @Bean("longMemoryVectorStore")
    public PgVectorStore longMemoryVectorStore(ZhiPuAiEmbeddingModel embeddingModel,
                                               @Qualifier("pgVectorJdbcTemplate") JdbcTemplate jdbcTemplate,
                                               @Value("${spring.ai.zhipuai.embedding.options.dimensions}") int dimension) {

        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .vectorTableName("agent_long_memory_vector")
                .dimensions(dimension)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .build();
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
