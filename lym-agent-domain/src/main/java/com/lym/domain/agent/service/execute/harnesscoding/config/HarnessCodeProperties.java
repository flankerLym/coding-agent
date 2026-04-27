package com.lym.domain.agent.service.execute.harnesscoding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.agent.harness-code")
public class HarnessCodeProperties {
    private String workspaceRoot = "/tmp/lym-agent-harnesscode/workspaces";
    private String uploadRoot = "/tmp/lym-agent-harnesscode/uploads";
    private Integer maxScanFiles = 300;
    private Integer maxFileBytes = 64 * 1024;
    private Boolean enableCommandExecute = false;
    private Integer commandTimeoutSeconds = 120;
}
