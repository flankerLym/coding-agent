package com.lym.test.skill;

import com.lym.Application;
import com.lym.domain.agent.service.execute.legalFlow.utils.LegalSkillExecutionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class LegalSkillHardCodeClientTest {

    private static final String SKILL_ID = "legal-compliance";

    @Resource
    private LegalSkillExecutionService legalSkillExecutionService;

    @Test
    public void test_client_call_legal_compliance_skill_without_tool_call_and_without_action() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("ee47e5200a65463181b692571a4da7f8.dRETn3mDdI1voRD3")
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

        ChatClient chatClient = ChatClient.builder(chatModel)
                .build();

        String systemPrompt = """
                你是合同审查 Agent。基于用户问题、当前任务和服务端装配的 Skill 包内容，识别合同风险并生成结构化草稿。

                要求：
                1. 服务端只会装配 Skill 包，不会指定 Skill 包内部 action。
                2. 你需要根据用户问题和 Skill 包内容，自动选择最合适的能力。
                3. 不得编造合同条款、事实或法律依据。
                4. 必须区分已知事实和需要补充的信息。
                5. 重点关注付款、违约责任、解除、争议解决、保密、知识产权、管辖、期限、交付验收、数据安全等。
                6. 输出必须严格是 JSON，不要输出 Markdown，不要输出解释性文字。

                输出 JSON：
                {"draft_type":"contract_review","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String baseUserPrompt = """
                用户原始问题：
                请审查下面这段合同条款：

                甲方委托乙方开发一套企业合同审查系统，合同金额为人民币 100000 元。
                甲方应在系统上线后一次性支付全部费用。
                如乙方延期交付，每延期一日支付合同总金额 0.01% 的违约金。
                系统上线后，所有源代码和知识产权归乙方所有。
                如发生争议，双方应提交乙方所在地法院解决。
                合同未约定验收标准、交付时间、保密义务和数据安全责任。

                请基于已装配的 legal-compliance Skill 包自动选择合适能力，并输出合同审查 JSON。
                """;

        String enhancedUserPrompt = legalSkillExecutionService.buildSkillAwareUserPrompt(
                SKILL_ID,
                baseUserPrompt
        );

        log.info("增强后的 UserPrompt：{}", enhancedUserPrompt);

        Assert.assertNotNull(enhancedUserPrompt);
        Assert.assertTrue("增强 Prompt 应包含 skill_id", enhancedUserPrompt.contains("skill_id: legal-compliance"));
        Assert.assertTrue("增强 Prompt 应包含 SKILL.md", enhancedUserPrompt.contains("SKILL.md"));
        Assert.assertFalse("测试中不应再出现固定 skill_action", enhancedUserPrompt.contains("skill_action:"));

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user(enhancedUserPrompt)
                .call()
                .content();

        log.info("Agent 回答：{}", answer);

        Assert.assertNotNull(answer);
        Assert.assertTrue("模型输出应包含 draft_type", answer.contains("draft_type"));
        Assert.assertTrue("模型输出应包含 contract_review", answer.contains("contract_review"));
    }

}