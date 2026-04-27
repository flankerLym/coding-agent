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
public class HarnessCodeProjectSnapshot {
    private String projectRoot;
    @Builder.Default
    private List<String> files = new ArrayList<>();
    @Builder.Default
    private List<String> importantFiles = new ArrayList<>();
    @Builder.Default
    private List<String> techStacks = new ArrayList<>();

    public String getTechStackSummary() {
        return techStacks == null || techStacks.isEmpty() ? "unknown" : String.join(", ", techStacks);
    }
}
