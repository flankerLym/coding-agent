package com.lym.domain.agent.service.execute.harnesscoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessCodeWorkspace {
    private String workspaceDir;
    private String projectRoot;
    private String artifactDir;
    private String logDir;
}
