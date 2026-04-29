package com.lym.domain.agent.service.execute.legalFlow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalToolConfig {
    private String mcpId;
    private String toolName;
    private String toolDesc;
    private String riskLevel;
    private Boolean required;
}
