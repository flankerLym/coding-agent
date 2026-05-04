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
                你是法律法条检索智能体。

                你的任务不是直接凭空回答，而是先把用户问题转成检索参数，然后调用 legal_web_code_search 工具。

                工具调用规则：
                1. keywords：提取 2 到 8 个法律关键词，不一定照抄用户原文。
                2. lawCodes：选择要查询的法律文档编码，多个用英文逗号分隔。
                3. articleNo：如果用户明确提到法条编号则填写，否则为空字符串。
                4. topK：默认 5。

                lawCodes 选择规则：
                - 枪击、走私、诈骗、盗窃、故意伤害、非法持有枪支、犯罪、刑罚：criminal_law
                - 合同、侵权、婚姻、继承、物权、人格权、民事责任：civil_code
                - 起诉、管辖、执行、证据、上诉、财产保全、民事诉讼程序：civil_procedure_law
                - 劳动合同、工资、辞退、经济补偿、双倍工资：labor_contract_law

                必须调用 legal_web_code_search 工具。
                回答必须基于工具返回的法条结果。
                如果工具没有返回结果，就说明未检索到明确法条依据，不要编造法条。
                """;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .user("公司一年没和我签劳动合同，我能要求双倍工资吗？")
                .call()
                .content();

        log.info("Agent 回答：{}", answer);
    }
}