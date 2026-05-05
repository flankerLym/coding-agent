package com.lym.domain.agent.service.execute.legalFlow.utils;

import com.lym.domain.agent.service.execute.legalFlow.model.skills.LegalSkillResource;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
public class LegalSkillExecutionService {

    private static final int MAX_SKILL_SECTION_LENGTH = 4000;

    @Resource
    private LegalSkillResourceLoader legalSkillResourceLoader;

    /**
     * 缓存已经组装好的 Skill Prompt Block。
     *
     * key: skillId::skillAction
     * value: 已经截断、格式化后的 Skill 增强片段
     */
    private final Map<String, String> skillPromptBlockCache = new ConcurrentHashMap<>();

    /**
     * systemPrompt 保持当前节点优先。
     *
     * 如果 Skill metadata.yaml 中配置了 system_prompt，则用 Skill 的 system_prompt。
     * 否则使用节点默认 systemPrompt。
     */
    public String systemPromptOrDefault(String skillId, Supplier<String> defaultPromptSupplier) {
        LegalSkillResource skillResource = legalSkillResourceLoader.load(skillId);

        if (skillResource.hasSystemPrompt()) {
            return skillResource.getSystemPrompt();
        }

        return defaultPromptSupplier.get();
    }

    /**
     * 执行节点级 Skill 增强。
     *
     * 这里不会创建新的 ChatClient Bean。
     * 这里也不会把 Skill 暴露成 ToolCall。
     *
     * 它只做一件事：
     * 把当前节点指定的 Skill 内容追加到本次 userPrompt 中。
     *
     * Skill 内容的加载和组装会被缓存，避免每次重复拼接完整 Skill 块。
     */
    public String buildSkillAwareUserPrompt(String skillId,
                                            String skillAction,
                                            String basePrompt) {
        String skillPromptBlock = buildSkillPromptBlockOnce(skillId, skillAction);

        if (!hasText(skillPromptBlock)) {
            return basePrompt;
        }

        return basePrompt + skillPromptBlock;
    }

    private String buildSkillPromptBlockOnce(String skillId, String skillAction) {
        String normalizedSkillId = normalizeSkillId(skillId);
        String normalizedSkillAction = normalizeSkillAction(skillAction);

        String cacheKey = normalizedSkillId + "::" + normalizedSkillAction;

        return skillPromptBlockCache.computeIfAbsent(cacheKey, key -> {
            LegalSkillResource skillResource = legalSkillResourceLoader.load(normalizedSkillId);

            if (!skillResource.isAvailable()) {
                log.warn("Skill 不可用，跳过节点级 Skill 增强，skillId={}, skillAction={}",
                        normalizedSkillId, normalizedSkillAction);
                return "";
            }

            StringBuilder prompt = new StringBuilder();

            prompt.append("\n\n==================== 当前执行节点已装配 Skill ====================\n");
            prompt.append("skill_id: ").append(skillResource.getSkillId()).append("\n");

            if (hasText(normalizedSkillAction)) {
                prompt.append("skill_action: ").append(normalizedSkillAction).append("\n");
            }

            appendSkillSection(prompt, "SKILL.md", skillResource.getSkillMarkdown());
            appendSkillSection(prompt, "metadata.yaml", skillResource.getMetadataYaml());
            appendSkillSection(prompt, "reference.yaml", skillResource.getReferenceYaml());
            appendSkillSection(prompt, "script.py", skillResource.getScriptPython());

            prompt.append("\n\nSkill 执行要求：\n");
            prompt.append("1. 当前 Skill 已由服务端执行节点装配，你不能自行请求加载、切换或声明其他 Skill。\n");
            prompt.append("2. 当前业务节点指定的 skill_action 是当前优先能力；如果 SKILL.md 中包含该能力说明，必须优先按该能力执行。\n");
            prompt.append("3. 如果 SKILL.md 中包含多个能力，只能选择与当前用户问题和当前节点任务最匹配的能力。\n");
            prompt.append("4. 如果 metadata.yaml 中存在 system_prompt，应优先遵守 metadata.yaml 的 system_prompt。\n");
            prompt.append("5. 如果 reference.yaml 存在，将其作为流程路由、风险检查项和输出规范参考。\n");
            prompt.append("6. 如果 script.py 存在，当前阶段仅作为确定性规则参考，不在 Java 进程中直接执行 Python。\n");
            prompt.append("7. 不得编造合同条款、事实、法律依据、案例或脚本执行结果。\n");
            prompt.append("8. 最终必须严格输出当前节点要求的 JSON，不要输出 Markdown、解释性文字或代码块。\n");

            log.info("执行节点级 Skill Prompt Block 组装完成并缓存，skillId={}, skillAction={}, length={}",
                    normalizedSkillId, normalizedSkillAction, prompt.length());

            return prompt.toString();
        });
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

    private String normalizeSkillId(String skillId) {
        if (!hasText(skillId)) {
            return "";
        }

        String value = skillId.trim();

        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("非法 skillId: " + skillId);
        }

        return value;
    }

    private String normalizeSkillAction(String skillAction) {
        if (!hasText(skillAction)) {
            return "";
        }

        String value = skillAction.trim();

        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("非法 skillAction: " + skillAction);
        }

        return value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 本地开发调试时，如果改了 resources/skills 下的文件，
     * 可以手动调用这个方法清理缓存。
     *
     * 生产环境一般不需要调用。
     */
    public void clearSkillPromptBlockCache() {
        skillPromptBlockCache.clear();
        log.info("执行节点级 Skill Prompt Block 缓存已清空");
    }
}