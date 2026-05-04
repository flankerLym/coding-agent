package com.lym.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "legal.web-code-search")
public class LegalWebCodeSearchProperties {

    /**
     * 网页缓存时间，单位秒
     */
    private Integer cacheSeconds = 3600;

    /**
     * 单个网页最大读取字节数
     */
    private Long maxPageSizeBytes = 5242880L;

    /**
     * lawCode -> 文档配置
     */
    private Map<String, DocumentConfig> documents = new HashMap<>();

    @Data
    public static class DocumentConfig {

        private String name;

        private String url;

        private Boolean enabled = true;
    }
}