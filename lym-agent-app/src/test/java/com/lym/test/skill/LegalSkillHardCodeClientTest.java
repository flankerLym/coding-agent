package com.lym.test.skill;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
public class LegalSkillHardCodeClientTest {

    @Test
    public void test_client_call_hard_code_skill() {

        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey("这里换成你的API_KEY")
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

        // 这里手动 new 一个硬编码 Skill
        HardCodeLegalSearchSkill hardCodeLegalSearchSkill = new HardCodeLegalSearchSkill();

        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultTools(hardCodeLegalSearchSkill)
                .build();

        String systemPrompt = """
                你是法律问答 Agent。你可以调用 hard_code_legal_search_skill 工具检索法律依据。

                要求：
                1. 遇到法律问题时，优先调用工具检索依据。
                2. 不作绝对结论。
                3. 不编造法条、案例或事实。
                4. 回答必须优先基于工具返回的法条结果。
                5. 如果工具未返回明确依据，应说明“当前未检索到明确法条依据”。
                6. 按规则/适用条件/风险/下一步建议组织。

                输出 JSON：
                {"draft_type":"legal_qa","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user("我在五一劳动节加班，我该怎么维护自己的权益，我能得到哪些补偿？")
                .call()
                .content();

        log.info("Agent 回答：{}", answer);
    }

    public static class HardCodeLegalSearchSkill {

        @Tool(
                name = "hard_code_legal_search_skill",
                description = """
                        硬编码法律检索 Skill。
                        用于根据关键词和 lawCodes 返回预置法条材料。

                        可用 lawCodes：
                        - labor_law：劳动法，适合加班费、法定节假日、工资报酬问题。
                        - labor_contract_law：劳动合同法，适合劳动合同、工资支付、拖欠工资问题。

                        使用规则：
                        - 五一、国庆、春节、法定节假日、加班费、三倍工资：优先使用 labor_law。
                        - 劳动合同、工资支付、拖欠工资、用人单位：可以使用 labor_contract_law。
                        - 多个 lawCodes 用英文逗号分隔。
                        """
        )
        public List<Map<String, Object>> search(
                @ToolParam(
                        description = "法律检索关键词，例如：五一 加班 法定节假日 三倍工资",
                        required = true
                )
                String keywords,

                @ToolParam(
                        description = "法律文档编码，例如：labor_law,labor_contract_law",
                        required = true
                )
                String lawCodes,

                @ToolParam(
                        description = "返回数量，默认5，最大10",
                        required = false
                )
                Integer topK
        ) {
            int limit = topK == null || topK <= 0 ? 5 : Math.min(topK, 10);

            List<Map<String, Object>> all = new ArrayList<>();

            if (lawCodes != null && lawCodes.contains("labor_law")) {
                all.add(Map.of(
                        "lawCode", "labor_law",
                        "lawName", "中华人民共和国劳动法",
                        "articleNo", "第四十四条",
                        "content", "《中华人民共和国劳动法》第四十四条：有下列情形之一的，用人单位应当按照下列标准支付高于劳动者正常工作时间工资的工资报酬：（一）安排劳动者延长工作时间的，支付不低于工资的百分之一百五十的工资报酬；（二）休息日安排劳动者工作又不能安排补休的，支付不低于工资的百分之二百的工资报酬；（三）法定休假日安排劳动者工作的，支付不低于工资的百分之三百的工资报酬。",
                        "matchType", "keyword",
                        "score", 100
                ));
            }

            if (lawCodes != null && lawCodes.contains("labor_contract_law")) {
                all.add(Map.of(
                        "lawCode", "labor_contract_law",
                        "lawName", "中华人民共和国劳动合同法",
                        "articleNo", "第三十一条",
                        "content", "《中华人民共和国劳动合同法》第三十一条：用人单位应当严格执行劳动定额标准，不得强迫或者变相强迫劳动者加班。用人单位安排加班的，应当按照国家有关规定向劳动者支付加班费。",
                        "matchType", "keyword",
                        "score", 80
                ));
            }

            return all.stream()
                    .limit(limit)
                    .toList();
        }
    }
}