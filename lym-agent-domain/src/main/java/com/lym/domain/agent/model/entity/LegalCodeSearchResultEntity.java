package com.lym.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 法条检索结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalCodeSearchResultEntity {

    private String lawCode;

    private String lawName;

    private String sourceUrl;

    private String articleNo;

    private String content;

    /**
     * article_no / keyword
     */
    private String matchType;

    private Integer score;
}