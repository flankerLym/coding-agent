package com.lym.domain.agent.service.execute.legalFlow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalSkillConfig {
    private String skillCode;
    private String skillName;
    private String skillType;
    private String promptId;
    private String modelId;
    private String description;
}
