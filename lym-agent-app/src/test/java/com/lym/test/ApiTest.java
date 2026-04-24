package com.lym.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    private OpenAiChatModel openAiChatModel;

    @Test
    public void test() {

        String apiKey = "ee47e5200a65463181b692571a4da7f8.dRETn3mDdI1voRD3 ";

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.z.ai/api/paas/v4")
                .apiKey(new SimpleApiKey(apiKey))
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();

        openAiChatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("glm-4.7")
                        .temperature(0.7)
                        .maxTokens(1024)
                        .build())
                .build();

        ChatResponse response = openAiChatModel.call(
                new Prompt("你好，请用一句话介绍一下你自己。")
        );

        String content = response.getResult()
                .getOutput()
                .getText();

        System.out.println("智谱 GLM-4.7 返回结果：");
        System.out.println(content);
    }
}
