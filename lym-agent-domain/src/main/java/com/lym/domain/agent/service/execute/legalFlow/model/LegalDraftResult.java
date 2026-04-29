package com.lym.domain.agent.service.execute.legalFlow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LegalDraftResult {
    private String draftType;
    private String draftAnswer;
    private List<String> keyFindings = new ArrayList<>();
    private List<String> riskPoints = new ArrayList<>();
    private List<String> missingInfo = new ArrayList<>();
}
