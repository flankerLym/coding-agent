package com.lym.domain.agent.service.execute.harnesscoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessCodeStep {
    private String title;
    private String instruction;
    private String toolName;
    private String expectedOutput;
}
