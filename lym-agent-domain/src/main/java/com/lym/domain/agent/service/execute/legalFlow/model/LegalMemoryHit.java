package com.lym.domain.agent.service.execute.legalFlow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalMemoryHit {
    private String memoryId;
    private String title;
    private String content;
    private String sourceType;
    private Double score;
}
