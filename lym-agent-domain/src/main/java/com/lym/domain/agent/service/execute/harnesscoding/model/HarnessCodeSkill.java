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
public class HarnessCodeSkill {
    private String name;
    private String description;
    @Builder.Default
    private List<String> requiredTools = new ArrayList<>();
    @Builder.Default
    private List<String> verifyCommands = new ArrayList<>();
}
