package com.lym.domain.agent.service.execute.legalFlow.utils;

import com.lym.domain.agent.service.execute.legalFlow.model.skills.LegalSkillResource;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class LegalSkillExecutionService {

    private static final int MAX_SKILL_SECTION_LENGTH = 4000;

    @Resource
    private LegalSkillResourceLoader legalSkillResourceLoader;

    public String systemPromptOrDefault(String skillId, Supplier<String> defaultPromptSupplier) {
        LegalSkillResource skillResource = legalSkillResourceLoader.load(skillId);

        if (skillResource.hasSystemPrompt()) {
            return skillResource.getSystemPrompt();
        }

        return defaultPromptSupplier.get();
    }

    public String buildSkillAwareUserPrompt(String skillId,
                                            String skillAction,
                                            String basePrompt) {
        LegalSkillResource skillResource = legalSkillResourceLoader.load(skillId);

        if (!skillResource.isAvailable()) {
            return basePrompt;
        }

        StringBuilder prompt = new StringBuilder(basePrompt);

        prompt.append("\n\n==================== 当前装配 Skill ====================\n");
        prompt.append("skill_id: ").append(skillResource.getSkillId()).append("\n");

        if (hasText(skillAction)) {
            prompt.append("skill_action: ").append(skillAction).append("\n");
        }

        appendSkillSection(prompt, "SKILL.md", skillResource.getSkillMarkdown());
        appendSkillSection(prompt, "metadata.yaml", skillResource.getMetadataYaml());
        appendSkillSection(prompt, "reference.yaml", skillResource.getReferenceYaml());
        appendSkillSection(prompt, "script.py", skillResource.getScriptPython());

        prompt.append("\n\nSkill 执行要求：\n");
        prompt.append("1. 当前业务节点只负责选择 skill_id 和 skill_action，不绑定具体 Skill 文件结构。\n");
        prompt.append("2. 如果 SKILL.md 中存在与 skill_action 匹配的能力说明，优先按该能力执行。\n");
        prompt.append("3. 如果 metadata.yaml 中存在 system_prompt，优先遵守 metadata.yaml 的 system_prompt。\n");
        prompt.append("4. 如果 reference.yaml 存在，将其作为流程路由、风险检查项和输出规范参考。\n");
        prompt.append("5. 如果 script.py 存在，当前阶段仅作为确定性规则参考，不在 Java 进程中直接执行 Python。\n");
        prompt.append("6. 最终必须严格输出 JSON，不要输出 Markdown、解释性文字或代码块。\n");

        return prompt.toString();
    }

    private void appendSkillSection(StringBuilder prompt, String title, String content) {
        if (!hasText(content)) {
            return;
        }

        prompt.append("\n\n-------------------- ")
                .append(title)
                .append(" --------------------\n")
                .append(limit(content, MAX_SKILL_SECTION_LENGTH));
    }

    private String limit(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        if (content.length() <= maxLength) {
            return content;
        }

        return content.substring(0, maxLength)
                + "\n\n[内容过长，已截断，原始长度="
                + content.length()
                + "]";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}