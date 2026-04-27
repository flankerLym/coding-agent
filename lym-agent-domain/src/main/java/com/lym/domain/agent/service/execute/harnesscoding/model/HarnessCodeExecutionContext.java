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
public class HarnessCodeExecutionContext {
    private String aiAgentId;
    private String sessionId;
    private String rawMessage;
    private String task;
    private Integer maxStep;
    private String zipPath;
    private String projectPath;
    private String gitUrl;
    private String branch;
    private HarnessCodeWorkspace workspace;
    private HarnessCodeProjectSnapshot snapshot;
    private HarnessCodeSkill selectedSkill;
    private HarnessCodePlan plan;
    private HarnessCodeVerifyResult verifyResult;
    @Builder.Default
    private List<HarnessCodeToolResult> toolResults = new ArrayList<>();
}
