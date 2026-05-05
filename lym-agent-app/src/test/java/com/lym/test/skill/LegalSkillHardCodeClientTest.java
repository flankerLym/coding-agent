package com.lym.test.skill;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class LegalSkillHardCodeClientTest {

    @Test
    public void test_client_call_classpath_legal_compliance_skill() {

        String apiKey = System.getenv("Z_AI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("ee47e5200a65463181b692571a4da7f8.dRETn3mDdI1voRD3");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.z.ai/api/paas/v4")
                .completionsPath("/chat/completions")
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model("glm-4.7")
                .temperature(0.2)
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();

        // 这里不再硬编码法律材料，而是加载项目 resources/skills 下的真实 Skill
        ClasspathLegalComplianceSkill classpathLegalComplianceSkill = new ClasspathLegalComplianceSkill();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(classpathLegalComplianceSkill)
                .build();

        String systemPrompt = """
                你是合同审查 Agent。你必须先调用 legal_compliance_skill_loader 工具加载项目中的法律合规 Skill。

                工具调用要求：
                1. skillId 固定传入：legal-compliance。
                2. skillAction 固定传入：contract-review。
                3. 工具返回 SKILL.md、metadata.yaml、reference.yaml、script.py 等资源后，必须基于这些 Skill 资源执行合同审查。
                4. 不得编造合同条款、事实或法律依据。
                5. 如果合同文本信息不足，应放入 missing_info。
                6. 输出必须严格是 JSON，不要输出 Markdown，不要输出解释性文字。

                输出 JSON：
                {"draft_type":"contract_review","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String userPrompt = """
                请使用 legal-compliance Skill 中的 contract-review 能力，审查下面这段合同条款：

                甲方委托乙方开发一套企业合同审查系统，合同金额为人民币 100000 元。
                甲方应在系统上线后一次性支付全部费用。
                如乙方延期交付，每延期一日支付合同总金额 0.01% 的违约金。
                系统上线后，所有源代码和知识产权归乙方所有。
                如发生争议，双方应提交乙方所在地法院解决。
                合同未约定验收标准、交付时间、保密义务和数据安全责任。

                请输出合同审查 JSON。
                """;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .call()
                .content();

        log.info("Agent 回答：{}", answer);
    }

    public static class ClasspathLegalComplianceSkill {

        @Tool(
                name = "legal_compliance_skill_loader",
                description = """
                        从项目 classpath 加载法律合规 Skill 资源。

                        当前主要用于加载：
                        - skills/legal-compliance/SKILL.md

                        同时兼容以下可选文件：
                        - metadata.yaml
                        - metadata.yml
                        - reference.yaml
                        - reference.yml
                        - script.py
                        - references/REFERENCE.md
                        - scripts/script.py

                        该工具只负责读取 Skill 资源，不负责直接给出法律结论。
                        """
        )
        public Map<String, Object> load(
                @ToolParam(
                        description = "Skill ID，例如 legal-compliance",
                        required = true
                )
                String skillId,

                @ToolParam(
                        description = "Skill 动作，例如 contract-review、legal-qa、labor-contract-check",
                        required = true
                )
                String skillAction
        ) {
            String normalizedSkillId = normalizeSkillId(skillId);
            String normalizedSkillAction = normalizeSkillAction(skillAction);

            String basePath = "skills/" + normalizedSkillId + "/";

            String skillMarkdown = readFirstExisting(basePath,
                    "SKILL.md",
                    "skill.md"
            );

            String metadataYaml = readFirstExisting(basePath,
                    "metadata.yaml",
                    "metadata.yml"
            );

            String reference = readFirstExisting(basePath,
                    "reference.yaml",
                    "reference.yml",
                    "references/REFERENCE.md",
                    "references/reference.md"
            );

            String scriptPython = readFirstExisting(basePath,
                    "script.py",
                    "scripts/script.py",
                    "scripts/contract_review.py"
            );

            boolean available = hasText(skillMarkdown)
                    || hasText(metadataYaml)
                    || hasText(reference)
                    || hasText(scriptPython);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("available", available);
            result.put("skillId", normalizedSkillId);
            result.put("skillAction", normalizedSkillAction);
            result.put("basePath", basePath);
            result.put("skillMarkdown", skillMarkdown);
            result.put("metadataYaml", metadataYaml);
            result.put("reference", reference);
            result.put("scriptPython", scriptPython);

            result.put("executionRules", """
                    1. 优先按 skillAction 匹配 SKILL.md 中的能力说明。
                    2. 如果 metadataYaml 存在 system_prompt，应优先遵守。
                    3. 如果 reference 存在，应作为流程路由和风险检查参考。
                    4. 如果 scriptPython 存在，当前测试仅作为规则参考，不在 Java 进程中直接执行 Python。
                    5. 最终必须输出调用方要求的 JSON 结构。
                    """);

            log.info("加载 Skill 完成，skillId={}, skillAction={}, available={}, basePath={}",
                    normalizedSkillId, normalizedSkillAction, available, basePath);

            return result;
        }

        private String normalizeSkillId(String skillId) {
            if (!hasText(skillId)) {
                throw new IllegalArgumentException("skillId 不能为空");
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

        private String readFirstExisting(String basePath, String... relativePaths) {
            for (String relativePath : relativePaths) {
                String content = readIfExists(basePath + relativePath);
                if (hasText(content)) {
                    return content;
                }
            }

            return "";
        }

        private String readIfExists(String classpathLocation) {
            try {
                ClassPathResource resource = new ClassPathResource(classpathLocation);

                if (!resource.exists()) {
                    return "";
                }

                try (InputStream inputStream = resource.getInputStream()) {
                    return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("读取 Skill 资源失败，path={}, reason={}", classpathLocation, e.getMessage());
                return "";
            }
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }
    }
}