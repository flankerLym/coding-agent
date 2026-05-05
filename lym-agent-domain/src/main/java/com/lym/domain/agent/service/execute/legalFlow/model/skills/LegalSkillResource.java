package com.lym.domain.agent.service.execute.legalFlow.model.skills;

public class LegalSkillResource {

    private final String skillId;

    private final String skillMarkdown;

    private final String metadataYaml;

    private final String referenceYaml;

    private final String scriptPython;

    private final String systemPrompt;

    public LegalSkillResource(String skillId,
                              String skillMarkdown,
                              String metadataYaml,
                              String referenceYaml,
                              String scriptPython,
                              String systemPrompt) {
        this.skillId = skillId;
        this.skillMarkdown = skillMarkdown;
        this.metadataYaml = metadataYaml;
        this.referenceYaml = referenceYaml;
        this.scriptPython = scriptPython;
        this.systemPrompt = systemPrompt;
    }

    public static LegalSkillResource empty(String skillId) {
        return new LegalSkillResource(skillId, "", "", "", "", "");
    }

    public String getSkillId() {
        return skillId;
    }

    public String getSkillMarkdown() {
        return skillMarkdown;
    }

    public String getMetadataYaml() {
        return metadataYaml;
    }

    public String getReferenceYaml() {
        return referenceYaml;
    }

    public String getScriptPython() {
        return scriptPython;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public boolean hasSystemPrompt() {
        return systemPrompt != null && !systemPrompt.trim().isEmpty();
    }

    public boolean isAvailable() {
        return hasText(skillMarkdown)
                || hasText(metadataYaml)
                || hasText(referenceYaml)
                || hasText(scriptPython);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}