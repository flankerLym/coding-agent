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
     * key: skillId
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
     * 开发者只指定 skillId。
     * Skill 包内具体 action / ability 的选择权交给大模型。
     */
    public String buildSkillAwareUserPrompt(String skillId,
                                            String basePrompt) {
        String skillPromptBlock = buildSkillPromptBlockOnce(skillId);

        if (!hasText(skillPromptBlock)) {
            return basePrompt;
        }

        return basePrompt + skillPromptBlock;
    }

    private String buildSkillPromptBlockOnce(String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);

        return skillPromptBlockCache.computeIfAbsent(normalizedSkillId, key -> {
            LegalSkillResource skillResource = legalSkillResourceLoader.load(normalizedSkillId);

            if (!skillResource.isAvailable()) {
                log.warn("Skill 不可用，跳过节点级 Skill 增强，skillId={}", normalizedSkillId);
                return "";
            }

            StringBuilder prompt = new StringBuilder();

            prompt.append("\n\n==================== 当前执行节点已装配 Skill 包 ====================\n");
            prompt.append("skill_id: ").append(skillResource.getSkillId()).append("\n");

            appendSkillSection(prompt, "SKILL.md", skillResource.getSkillMarkdown());
            appendSkillSection(prompt, "metadata.yaml", skillResource.getMetadataYaml());
            appendSkillSection(prompt, "reference.yaml", skillResource.getReferenceYaml());
            appendSkillSection(prompt, "script.py", skillResource.getScriptPython());

            prompt.append("\n\nSkill 包执行要求：\n");
            prompt.append("1. 当前 Skill 包已由服务端执行节点装配，你不能自行请求加载、切换或声明其他 Skill 包。\n");
            prompt.append("2. 开发者没有指定 Skill 内部 action，具体使用哪个能力必须由你根据用户问题、当前子任务、最近上下文和 SKILL.md 内容自动选择。\n");
            prompt.append("3. 如果 SKILL.md 中包含多个能力，例如 contract-review、contract-compare、contract-extract、contract-risk-score，应根据场景自动选择最匹配的能力。\n");
            prompt.append("4. 如果用户提供的是单份合同并要求审查，应优先选择合同审查类能力。\n");
            prompt.append("5. 如果用户提供两份合同并要求找差异，应优先选择合同对比类能力。\n");
            prompt.append("6. 如果用户要求提取甲乙方、金额、期限等信息，应优先选择合同信息提取类能力。\n");
            prompt.append("7. 如果用户要求风险分数，应优先选择合同风险评分类能力。\n");
            prompt.append("8. 如果 metadata.yaml 中存在 system_prompt，应优先遵守 metadata.yaml 的 system_prompt。\n");
            prompt.append("9. 如果 reference.yaml 存在，将其作为流程路由、风险检查项和输出规范参考。\n");
            prompt.append("10. 如果 script.py 存在，当前阶段仅作为确定性规则参考，不在 Java 进程中直接执行 Python。\n");
            prompt.append("11. 不得编造合同条款、事实、法律依据、案例或脚本执行结果。\n");
            prompt.append("12. 最终必须严格输出当前节点要求的 JSON，不要输出 Markdown、解释性文字或代码块。\n");

            log.info("执行节点级 Skill Prompt Block 组装完成并缓存，skillId={}, length={}",
                    normalizedSkillId, prompt.length());

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

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 本地开发调试时，如果改了 resources/skills 下的文件，
     * 可以手动调用这个方法清理缓存。
     */
    public void clearSkillPromptBlockCache() {
        skillPromptBlockCache.clear();
        log.info("执行节点级 Skill Prompt Block 缓存已清空");
    }
}