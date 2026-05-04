package com.lym.test.tools;

import com.lym.Application;

import com.lym.trigger.tool.LegalWebCodeSearchTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class LegalWebCodeSearchClientTest {



    @Resource
    private LegalWebCodeSearchTool legalWebCodeSearchTool;

    @Test
    public void test_client_call_legal_web_code_search_tool() {

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
                .defaultTools(legalWebCodeSearchTool)
                .build();

        String systemPrompt = """
                你是法律问答 Agent。基于检索材料和上下文生成法律问答草稿。
                你的调用工具是 legal_web_code_search 工具，用于检索法律材料和上下文。
                要求：
                1. 不作绝对结论。
                2. 不编造法条、案例或事实。
                3. 回答必须优先基于 legal_web_code_search 工具返回的法条结果。
                4. 如果工具未返回明确依据，应说明“当前未检索到明确法条依据”。
                5. 按规则/适用条件/风险/下一步建议组织。
                输出 JSON：{"draft_type":"legal_qa","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user("我在五一劳动节加班我该怎么维护自己的权益，我能得到哪些补偿")
                .call()
                .content();

        log.info("Agent 回答：{}", answer);
    }
}