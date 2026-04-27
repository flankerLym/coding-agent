package com.lym.domain.agent.service.execute.harnesscoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessCodePlan {
    @Builder.Default
    private List<HarnessCodeStep> steps = new ArrayList<>();

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder("执行计划：\n");
        for (int i = 0; i < steps.size(); i++) {
            HarnessCodeStep step = steps.get(i);
            sb.append(i + 1).append(". ").append(step.getTitle())
                    .append(" [tool=").append(step.getToolName()).append("]\n");
        }
        return sb.toString();
    }
}
