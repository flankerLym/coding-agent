package com.lym.test;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;



import org.springframework.ai.chat.client.ChatClient;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;

import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.lym.test.User;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import org.springframework.core.io.Resource;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

@Slf4j
@SpringBootTest

public class aiTest {

    @Value("classpath:data/dog.png")
    private Resource imageResource;

    @Value("classpath:data/file.txt")
    private Resource textResource;

    @Value("classpath:data/article-prompt-words.txt")
    private Resource articlePromptWordsResource;

    @Autowired
    private ZhiPuAiChatModel chatModel;

    @Autowired
    private PgVectorStore pgVectorStore;

    private final TokenTextSplitter tokenTextSplitter = new TokenTextSplitter();

    @Autowired
    private  ChatClient chatClient;


    @Test
    public void test_call() {
        ChatOptions chatOptions = ChatOptions.builder()
                .model("glm-4.7")
                .maxTokens(200)
                .build();
        ChatResponse response = chatModel.call(new Prompt("请告诉我你是谁",chatOptions));
        log.info("测试结果(call):{}", JSON.toJSONString(response));
    }

    @Test
    public void test_call_images() throws IOException {
        UserMessage userMessage = UserMessage.builder()
                .text("请描述这张图片的主要内容，并说明图中物品的可能用途。")
                .media(org.springframework.ai.content.Media.builder()
                        .mimeType(MimeType.valueOf(MimeTypeUtils.IMAGE_PNG_VALUE))
                        .data(imageResource)
                        .build())
                .build();


        // 3. 构建ChatOptions（模型名修正为官方标准）
        ChatOptions chatOptions = ChatOptions.builder()
                .model("glm-4v-flash") // 智谱多模态轻量版官方名
                .maxTokens(65536)      // 保留原有参数
                .build();

        // 4. 调用模型（保留原有chatModel.call逻辑）
        ChatResponse response = chatModel.call(new Prompt(userMessage, chatOptions));

        // 5. 打印结果（保留原有日志格式）
        log.info("测试结果(images):{}", JSON.toJSONString(response));

    }





    @Test
    public void test_stream() throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);

        Flux<ChatResponse> stream = chatModel.stream(new Prompt(
                "人类存在的意义是什么",
                ChatOptions.builder()
                        .model("glm-4.7")
                        .maxTokens(1000)
                        .build()));

        stream.subscribe(
                chatResponse -> {
                    String content = chatResponse.getResult().getOutput().getText();
                    if (content != null && !content.isEmpty()) {
                        log.info("输出:{}", content);  // 直接打印文本，无需 JSON 序列化
                    }
                },
                Throwable::printStackTrace,
                () -> {
                    countDownLatch.countDown();
                    log.info("测试结果(stream): done!");
                }
        );

        countDownLatch.await();
    }

    @Test
    public void upload() {
        // textResource、articlePromptWordsResource
        TikaDocumentReader reader = new TikaDocumentReader(articlePromptWordsResource);

        List<Document> documents = reader.get();
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "article-prompt-words"));

        pgVectorStore.accept(documentSplitterList);

        log.info("上传完成");
    }

    @Test
    public void chat() {
        String message = "王大瓜今年几岁";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == '知识库名称-v4'")
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(request);

        String documentsCollectors = null == documents ? "" : documents.stream().map(Document::getText).collect(Collectors.joining());

        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatResponse chatResponse = chatModel.call(new Prompt(
                messages,
                ChatOptions.builder()
                        .model("glm-4.7")
                        .build()));

        log.info("测试结果:{}", JSON.toJSONString(chatResponse));
    }

    @Test
    public void saveMessageToVectorStore() {
        // 1. 你要存的内容（必须和你后面检索的内容对应！）
        String content = "王大瓜今年25岁，住在北京，是一名软件工程师。";

        // 2. 构建文档（带 metadata，必须和你检索的 filter 一致）
        Document document = Document.builder()
                .text(content)  // 存入的真实内容
                .metadata(Map.of(
                        "knowledge", "知识库名称-v4"  // 必须和你检索的 filter 一样
                ))
                .build();

        // 3. 存入向量库
        pgVectorStore.add(List.of(document));

        System.out.println("✅ 存入向量库成功！");
        System.out.println("内容：" + content);
    }

    @Test
    public void test_client(){
        User user = chatClient.prompt()
                .system("你是一个数据生成助手。请严格返回标准 JSON 格式，不要包含任何解释、markdown 或其他文本。")

                // ✅ 2. 用户请求 + 格式示例
                .user(u -> u.text("""
                    生成一个虚拟用户信息，包含：
                    - username: 8-12位字母数字组合
                    - password: 12位含大小写字母+数字+特殊字符
                    
                    请只返回纯 JSON 对象，格式如下：
                    {"username":"abc123xyz","password":"Aa1@bc2#de3$"}
                    """))
                .call().entity(User.class);
        log.info("测试结果:{}", JSON.toJSONString(user));
    }

    @TestConfiguration
    static class TestChatConfig {

        @Bean
        public ChatClient testChatClient(ChatModel chatModel) {
            return ChatClient.builder(chatModel)
                    .defaultOptions(ChatOptions.builder()
                            .model("glm-4.7")
                            .maxTokens(500)
                            .temperature(0.1)
                            .topP(0.9)
                            .build())
                    .build();
        }
    }
}


