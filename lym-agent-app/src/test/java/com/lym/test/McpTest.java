package com.lym.test;

import com.alibaba.fastjson.JSON;
import com.lym.test.advisors.RagAnswerAdvisor;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;

import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.poi.ss.formula.functions.T;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.PromptChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class McpTest {

    private ZhiPuAiChatModel chatModel;

    private ChatClient chatClient;

    @Resource
    private PgVectorStore vectorStore;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Before
    public void init() {

        ZhiPuAiApi zhiPuAiApi = new ZhiPuAiApi("ee47e5200a65463181b692571a4da7f8.dRETn3mDdI1voRD3");
        var toolCallbacks = new SyncMcpToolCallbackProvider(sseMcpClient03(),sseMcpClient02(),stdioMcpClient()).getToolCallbacks();

        chatModel = new ZhiPuAiChatModel(zhiPuAiApi,
                ZhiPuAiChatOptions.builder()
                .model("glm-4.7")
                .temperature(0.3)
                .maxTokens(2048) // 限制输出token，防止溢出
                .toolCallbacks(toolCallbacks)
                .build()
        );


        chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                          你是一个 AI Agent 智能体，可以根据用户输入信息生成文章，并发送到 CSDN 平台以及完成微信公众号消息通知，今天是 {current_date}。
                        
                          你擅长使用Planning模式，帮助用户生成质量更高的文章。
                        
                          你的规划应该包括以下几个方面：
                          1. 分析用户输入的内容。
                          2. 根据分析结果，查询相关网页。
                          3. 根据查询结果生成技术文章。
                          4. 提取，文章标题（需要含带技术点）、文章内容、文章标签（多个用英文逗号隔开）、文章简述（100字）将以上内容发布文章到CSDN
                          5. 获取发送到 CSDN 文章的 URL 地址。
                          6. 微信公众号消息通知，平台：CSDN、主题：为文章标题、描述：为文章简述、跳转地址：从发布文章到CSDN获取 URL 地址
                        """)
//                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(sseMcpClient03(),sseMcpClient02(),stdioMcpClient()).getToolCallbacks())
                .defaultAdvisors(
                        PromptChatMemoryAdvisor.builder(
                                MessageWindowChatMemory.builder()
                                        .maxMessages(100)
                                        .build()
                        ).build(),
                        new RagAnswerAdvisor(vectorStore, SearchRequest.builder()
                                .topK(5)
                                .filterExpression("knowledge == '知识库名称-v4'")
                                .build()),
                        SimpleLoggerAdvisor.builder().build())
                .build();
    }

    @Test
    public void test_chat_model_stream_01() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(
                        """
                                你去给我爬取https://www.baidu.com页面信息，把上面前五名的百度热搜新闻标题返回给我
                                """))
                .build();

        // 非流式，chatModel.call(prompt)

        Flux<ChatResponse> stream = chatModel.stream(prompt);

        stream.subscribe(
                chatResponse -> {
                    AssistantMessage output = chatResponse.getResult().getOutput();
                    log.info("测试结果: {}", JSON.toJSONString(output.getText()));
                },
                Throwable::printStackTrace,
                () -> {
                    countDownLatch.countDown();
                    System.out.println("Stream completed");
                }
        );

        countDownLatch.await();
    }

    @Test
    public void test_chat_model_call() {
        Prompt prompt = Prompt.builder()
                .messages(new UserMessage(
                        """
                                给我生成简短的jvm面试题，发布到我的csdn博客上面
                                """))
                .build();

        ChatResponse chatResponse = chatModel.call(prompt);

        log.info("测试结果(call):{}", JSON.toJSONString(chatResponse));
    }

    @Test
    public void test_02() {
        String userInput = "王大瓜今年几岁";
        System.out.println("\n>>> QUESTION: " + userInput);
        System.out.println("\n>>> ASSISTANT: " + chatClient
                .prompt(userInput)
                .system(s -> s.param("current_date", LocalDate.now().toString()))
                .call().content());
    }

//    @Test
//    public void test_client03() {
//        ChatClient chatClient01 = ChatClient.builder(chatModel)
//                .defaultSystem("""
//                        你是一个专业的AI提示词优化专家。请帮我优化以下prompt，并按照以下格式返回：
//
//                        # Role: [角色名称]
//
//                        ## Profile
//                        - language: [语言]
//                        - description: [详细的角色描述]
//                        - background: [角色背景]
//                        - personality: [性格特征]
//                        - expertise: [专业领域]
//                        - target_audience: [目标用户群]
//
//                        ## Skills
//
//                        1. [核心技能类别]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//
//                        2. [辅助技能类别]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//                           - [具体技能]: [简要说明]
//
//                        ## Rules
//
//                        1. [基本原则]：
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//
//                        2. [行为准则]：
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//                           - [具体规则]: [详细说明]
//
//                        3. [限制条件]：
//                           - [具体限制]: [详细说明]
//                           - [具体限制]: [详细说明]
//                           - [具体限制]: [详细说明]
//                           - [具体限制]: [详细说明]
//
//                        ## Workflows
//
//                        - 目标: [明确目标]
//                        - 步骤 1: [详细说明]
//                        - 步骤 2: [详细说明]
//                        - 步骤 3: [详细说明]
//                        - 预期结果: [说明]
//
//
//                        ## Initialization
//                        作为[角色名称]，你必须遵守上述Rules，按照Workflows执行任务。
//
//                        请基于以上模板，优化并扩展以下prompt，确保内容专业、完整且结构清晰，注意不要携带任何引导词或解释，不要使用代码块包围。
//                        """)
//                .defaultAdvisors(
//                        PromptChatMemoryAdvisor.builder(
//                                MessageWindowChatMemory.builder()
//                                        .maxMessages(100)
//                                        .build()
//                        ).build(),
//                        new RagAnswerAdvisor(vectorStore, SearchRequest.builder()
//                                .topK(5)
//                                .filterExpression("knowledge == 'article-prompt-words'")
//                                .build())
//                )
//                .defaultOptions(OpenAiChatOptions.builder()
//                        .model("gpt-4.1")
//                        .build())
//                .build();
//
//        String content = chatClient01
//                .prompt("生成一篇文章")
//
//                .system(s -> s.param("current_date", LocalDate.now().toString()))
//                .advisors(a -> a
//                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, "chatId-101")
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
//                .call().content();
//
//        System.out.println("\n>>> ASSISTANT: " + content);
//
//        ChatClient chatClient02 = ChatClient.builder(chatModel)
//                .defaultSystem("""
//                          你是一个 AI Agent 智能体，可以根据用户输入信息生成文章，并发送到 CSDN 平台以及完成微信公众号消息通知，今天是 {current_date}。
//
//                          你擅长使用Planning模式，帮助用户生成质量更高的文章。
//
//                          你的规划应该包括以下几个方面：
//                          1. 分析用户输入的内容，生成技术文章。
//                          2. 提取，文章标题（需要含带技术点）、文章内容、文章标签（多个用英文逗号隔开）、文章简述（100字）将以上内容发布文章到CSDN
//                          3. 获取发送到 CSDN 文章的 URL 地址。
//                          4. 微信公众号消息通知，平台：CSDN、主题：为文章标题、描述：为文章简述、跳转地址：为发布文章到CSDN获取 URL地址 CSDN文章链接 https 开头的地址。
//                        """)
////                .defaultTools(new SyncMcpToolCallbackProvider(sseMcpClient01(), sseMcpClient02()))
//                .defaultAdvisors(
//                        PromptChatMemoryAdvisor.builder(
//                                MessageWindowChatMemory.builder()
//                                        .maxMessages(100)
//                                        .build()
//                        ).build(),
//                        new SimpleLoggerAdvisor()
//                )
//                .defaultOptions(OpenAiChatOptions.builder()
//                        .model("gpt-4.1")
//                        .build())
//                .build();
//
//        String userInput = "生成一篇文章，要求如下 \r\n" + content;
//        System.out.println("\n>>> QUESTION: " + userInput);
//        System.out.println("\n>>> ASSISTANT: " + chatClient02
//                .prompt(userInput)
//                .system(s -> s.param("current_date", LocalDate.now().toString()))
//                .advisors(a -> a
//                        .param(CHAT_MEMORY_CONVERSATION_ID_KEY, "chatId-101")
//                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
//                .call().content());
//    }


    public McpSyncClient sseMcpClient01() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://175.178.182.172:8102").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
    }

    public McpSyncClient sseMcpClient02() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://175.178.182.172:8101").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
    }

    public McpSyncClient sseMcpClient03() {

        HttpClientSseClientTransport sseClientTransport = HttpClientSseClientTransport.builder("http://175.178.182.172:3000").build();

        McpSyncClient mcpSyncClient = McpClient.sync(sseClientTransport).requestTimeout(Duration.ofMinutes(180)).build();

        var init = mcpSyncClient.initialize();
        System.out.println("SSE MCP Initialized: " + init);

        return mcpSyncClient;
    }

    public McpSyncClient stdioMcpClient() {

        // based on
        // https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
        var stdioParams = ServerParameters.builder("E:/Develop/node/nvm/v20.9.0/npx.cmd")
                .args("-y", "@modelcontextprotocol/server-filesystem", "E:/agentDemo/mcp", "E:/agentDemo/mcp")
                .build();

        var mcpClient = McpClient.sync(new StdioClientTransport(stdioParams))
                .requestTimeout(Duration.ofSeconds(10)).build();

        var init = mcpClient.initialize();

        System.out.println("Stdio MCP Initialized: " + init);

        return mcpClient;

    }
}
