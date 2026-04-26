package com.lym.test;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.Application;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class Neo4jVectorSkillRouterQaTest {

    private static final String SKILL_VECTOR_INDEX = "skill_embedding_index";

    @Resource
    private Neo4jClient neo4jClient;

    @Resource
    private EmbeddingModel embeddingModel;

    @Test
    public void testQuestionAnswerWithVectorSkillRouter() {
        String userQuestion = "你帮我看一下百度热搜前十";

        // 1. 确保 Neo4j Skill 节点有 embedding，并创建 vector index
        ensureSkillEmbeddingsAndVectorIndex();

        // 2. 用户问题 -> embedding -> Neo4j vector Top20
        List<SkillCandidate> top20 = vectorSearchSkills(userQuestion, 20);

        System.out.println("========== Neo4j Vector Top20 ==========");
        top20.forEach(System.out::println);

        if (top20.isEmpty()) {
            throw new RuntimeException("Neo4j vector index 没有召回任何 Skill，请检查 Skill.embedding 是否已写入。");
        }

        // 3. 规则兜底加分，取 Top5
        List<SkillCandidate> top5 = applyRuleFallbackAndTakeTopK(userQuestion, top20, 5);

        System.out.println("========== Rule Fallback Top5 ==========");
        top5.forEach(System.out::println);

        // 4. LLM rerank Top5，选出最终 MCP Tool
        SkillCandidate selected = llmRerankTop5(userQuestion, top5);

        System.out.println("========== LLM Rerank Selected ==========");
        System.out.println(selected);

        // 5. 根据 fullSseUrl 创建 MCP Client
        McpSyncClient mcpSyncClient = createSseMcpClient(selected.getFullSseUrl());
        Object initResult = mcpSyncClient.initialize();

        System.out.println("========== MCP 初始化结果 ==========");
        System.out.println(initResult);

        // 6. 创建 GLM-4.7 ChatModel
        OpenAiChatModel chatModel = createZhipuGlm47Model();

        // 7. 把最终选中的 MCP Tool 挂给本次 ChatClient
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultSystem("""
                        你是一个具备 MCP 工具调用能力的智能体。

                        用户的问题如果涉及实时网页、热搜、新闻、网页内容、网页爬取，
                        你必须调用当前已经挂载的 MCP 工具获取真实信息。
                        不允许凭空编造实时结果。

                        系统已经通过 Neo4j Skill 向量检索 + 规则兜底 + LLM rerank，
                        为你选择了最相关的工具：

                        skillCode: %s
                        skillName: %s
                        toolCode: %s
                        toolName: %s
                        serverCode: %s

                        你应该优先使用该 MCP 工具完成用户请求。
                        """.formatted(
                        selected.getSkillCode(),
                        selected.getSkillName(),
                        selected.getToolCode(),
                        selected.getToolName(),
                        selected.getServerCode()
                ))
                .defaultToolCallbacks(new SyncMcpToolCallbackProvider(mcpSyncClient))
                .build();

        // 8. 最终问答：这里不给模型写死 fullSseUrl，也不直接告诉它怎么查 Neo4j
        String answer = chatClient.prompt()
                .user(userQuestion)
                .call()
                .content();

        System.out.println("========== 最终回答 ==========");
        System.out.println(answer);
    }

    /**
     * 初始化 Skill.embedding 和 Neo4j vector index。
     * 第一次运行会给所有 enabled Skill 生成 embedding。
     * 后续只给缺失 embedding 的 Skill 补 embedding。
     */
    private void ensureSkillEmbeddingsAndVectorIndex() {
        float[] probeEmbedding = embeddingModel.embed("skill vector dimension probe");
        int dimension = probeEmbedding.length;

        createSkillVectorIndexIfNeeded(dimension);
        fillMissingSkillEmbeddings();

        // 等待索引可用
        try {
            neo4jClient.query("CALL db.awaitIndexes(120)").run();
        } catch (Exception e) {
            System.out.println("db.awaitIndexes 执行失败，可忽略后再手动 SHOW VECTOR INDEXES 检查：" + e.getMessage());
        }
    }

    private void createSkillVectorIndexIfNeeded(int dimension) {
        String cypher = """
                CREATE VECTOR INDEX %s IF NOT EXISTS
                FOR (s:Skill)
                ON (s.embedding)
                OPTIONS {
                    indexConfig: {
                        `vector.dimensions`: %d,
                        `vector.similarity_function`: 'cosine'
                    }
                }
                """.formatted(SKILL_VECTOR_INDEX, dimension);

        neo4jClient.query(cypher).run();

        System.out.println("========== Vector Index Ready ==========");
        System.out.println("indexName = " + SKILL_VECTOR_INDEX);
        System.out.println("dimension = " + dimension);
    }

    private void fillMissingSkillEmbeddings() {
        Collection<Map<String, Object>> rows = neo4jClient.query("""
                MATCH (m:McpServer {enabled: true})
                      -[:EXPOSES_SKILL]->(s:Skill {enabled: true})
                      -[:HAS_TOOL]->(t:Tool {enabled: true})
                WHERE s.embedding IS NULL OR s.searchText IS NULL
                WITH
                    s,
                    collect(DISTINCT m.serverCode) AS serverCodes,
                    collect(DISTINCT m.fullSseUrl) AS fullSseUrls,
                    collect(DISTINCT t.toolCode) AS toolCodes,
                    collect(DISTINCT t.toolName) AS toolNames
                RETURN
                    elementId(s) AS skillElementId,
                    s.skillCode AS skillCode,
                    s.skillName AS skillName,
                    coalesce(s.description, '') AS description,
                    coalesce(s.category, '') AS category,
                    coalesce(s.tags, '') AS tags,
                    serverCodes AS serverCodes,
                    fullSseUrls AS fullSseUrls,
                    toolCodes AS toolCodes,
                    toolNames AS toolNames
                """)
                .fetch()
                .all();

        if (rows == null || rows.isEmpty()) {
            System.out.println("没有需要补充 embedding 的 Skill。");
            return;
        }

        System.out.println("需要补充 embedding 的 Skill 数量 = " + rows.size());

        for (Map<String, Object> row : rows) {
            String skillElementId = str(row.get("skillElementId"));

            String searchText = buildSearchText(
                    str(row.get("skillCode")),
                    str(row.get("skillName")),
                    str(row.get("description")),
                    str(row.get("category")),
                    str(row.get("tags")),
                    str(row.get("serverCodes")),
                    str(row.get("toolCodes")),
                    str(row.get("toolNames"))
            );

            float[] embedding = embeddingModel.embed(searchText);

            neo4jClient.query("""
                    MATCH (s:Skill)
                    WHERE elementId(s) = $skillElementId
                    SET s.searchText = $searchText
                    WITH s
                    CALL db.create.setNodeVectorProperty(s, 'embedding', $embedding)
                    RETURN s.skillCode AS skillCode
                    """)
                    .bind(skillElementId).to("skillElementId")
                    .bind(searchText).to("searchText")
                    .bind(toDoubleList(embedding)).to("embedding")
                    .fetch()
                    .all();

            System.out.println("已写入 Skill embedding: " + row.get("skillCode") + " / " + row.get("skillName"));
        }
    }

    private List<SkillCandidate> vectorSearchSkills(String userQuestion, int topK) {
        float[] queryEmbedding = embeddingModel.embed(userQuestion);

        Collection<Map<String, Object>> rows = neo4jClient.query("""
                CALL db.index.vector.queryNodes($indexName, $topK, $embedding)
                YIELD node AS s, score
                MATCH (m:McpServer {enabled: true})
                      -[:EXPOSES_SKILL]->(s)
                      -[:HAS_TOOL]->(t:Tool {enabled: true})
                RETURN
                    m.serverCode AS serverCode,
                    m.fullSseUrl AS fullSseUrl,
                    s.skillCode AS skillCode,
                    s.skillName AS skillName,
                    coalesce(s.description, '') AS description,
                    coalesce(s.category, '') AS category,
                    coalesce(s.tags, '') AS tags,
                    s.searchText AS searchText,
                    t.toolCode AS toolCode,
                    t.toolName AS toolName,
                    score AS vectorScore
                ORDER BY vectorScore DESC
                """)
                .bind(SKILL_VECTOR_INDEX).to("indexName")
                .bind(topK).to("topK")
                .bind(toDoubleList(queryEmbedding)).to("embedding")
                .fetch()
                .all();

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        return rows.stream()
                .map(row -> SkillCandidate.builder()
                        .serverCode(str(row.get("serverCode")))
                        .fullSseUrl(str(row.get("fullSseUrl")))
                        .skillCode(str(row.get("skillCode")))
                        .skillName(str(row.get("skillName")))
                        .description(str(row.get("description")))
                        .category(str(row.get("category")))
                        .tags(str(row.get("tags")))
                        .searchText(str(row.get("searchText")))
                        .toolCode(str(row.get("toolCode")))
                        .toolName(str(row.get("toolName")))
                        .vectorScore(toDouble(row.get("vectorScore")))
                        .ruleScore(0.0)
                        .finalScore(toDouble(row.get("vectorScore")))
                        .build())
                .collect(Collectors.toList());
    }

    private List<SkillCandidate> applyRuleFallbackAndTakeTopK(String userQuestion,
                                                              List<SkillCandidate> candidates,
                                                              int topK) {
        for (SkillCandidate candidate : candidates) {
            double ruleScore = calcRuleScore(userQuestion, candidate);
            candidate.setRuleScore(ruleScore);
            candidate.setFinalScore(candidate.getVectorScore() + ruleScore);
        }

        return candidates.stream()
                .sorted(Comparator.comparingDouble(SkillCandidate::getFinalScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double calcRuleScore(String userQuestion, SkillCandidate candidate) {
        String question = userQuestion == null ? "" : userQuestion;

        String text = String.join(" ",
                candidate.getSkillCode(),
                candidate.getSkillName(),
                candidate.getDescription(),
                candidate.getCategory(),
                candidate.getTags(),
                candidate.getToolCode(),
                candidate.getToolName(),
                candidate.getSearchText()
        ).toLowerCase();

        double score = 0.0;

        if (containsAny(question, "百度", "热搜", "网页", "爬取", "抓取", "网站", "新闻", "搜索")) {
            if (containsAny(text, "web", "网页", "爬取", "抓取", "搜索", "新闻", "热搜", "crawler")) {
                score += 0.30;
            }
        }

        if (containsAny(question, "百度")) {
            if (containsAny(text, "百度", "baidu", "网页", "搜索", "热搜")) {
                score += 0.15;
            }
        }

        if (containsAny(question, "热搜")) {
            if (containsAny(text, "热搜", "新闻", "搜索", "网页", "crawler")) {
                score += 0.15;
            }
        }

        if (containsAny(question, "代码", "仓库", "github", "项目")) {
            if (containsAny(text, "code", "代码", "github", "repo", "仓库")) {
                score += 0.30;
            }
        }

        if (containsAny(question, "数据库", "sql", "表", "查询")) {
            if (containsAny(text, "database", "数据库", "sql", "mysql", "postgres", "neo4j")) {
                score += 0.30;
            }
        }

        return score;
    }

    private SkillCandidate llmRerankTop5(String userQuestion, List<SkillCandidate> top5) {
        if (top5 == null || top5.isEmpty()) {
            throw new RuntimeException("LLM rerank 失败：Top5 候选为空");
        }

        OpenAiChatModel chatModel = createZhipuGlm47Model();
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        String candidatesJson = JSON.toJSONString(top5);

        String prompt = """
                你是一个 MCP Tool Router。
                你的任务不是回答用户问题，而是从候选工具中选择最适合执行用户问题的工具。

                用户问题：
                %s

                候选工具 JSON：
                %s

                选择标准：
                1. 用户要实时信息、网页内容、热搜、新闻时，优先选择网页爬取/搜索类工具。
                2. 用户要代码仓库、文件、数据库时，选择对应工具。
                3. 不要编造候选工具之外的 toolCode。
                4. 只从候选 JSON 中选择一个。

                你必须只返回 JSON，不要返回解释。
                格式如下：
                {
                  "toolCode": "候选工具中的 toolCode",
                  "reason": "简短原因"
                }
                """.formatted(userQuestion, candidatesJson);

        String raw = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        System.out.println("========== LLM Rerank Raw ==========");
        System.out.println(raw);

        try {
            JSONObject jsonObject = JSON.parseObject(extractJsonObject(raw));
            String selectedToolCode = jsonObject.getString("toolCode");

            for (SkillCandidate candidate : top5) {
                if (candidate.getToolCode().equals(selectedToolCode)) {
                    candidate.setRerankReason(jsonObject.getString("reason"));
                    return candidate;
                }
            }

            System.out.println("LLM 返回的 toolCode 不在候选中，fallback 到 Top1：" + selectedToolCode);
            return top5.get(0);

        } catch (Exception e) {
            System.out.println("LLM rerank JSON 解析失败，fallback 到 Top1：" + e.getMessage());
            return top5.get(0);
        }
    }

    private OpenAiChatModel createZhipuGlm47Model() {
        String apiKey = "ee47e5200a65463181b692571a4da7f8.dRETn3mDdI1voRD3";

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("""
                    未读取到环境变量 ZHIPU_API_KEY。
                    请先在 PowerShell 设置：
                    $env:ZHIPU_API_KEY="你的智谱API_KEY"
                    """);
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://api.z.ai/api/paas/v4")
                .apiKey(new SimpleApiKey(apiKey))
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("glm-4.7")
                        .temperature(0.1)
                        .maxTokens(2048)
                        .build())
                .build();
    }

    private McpSyncClient createSseMcpClient(String fullSseUrl) {
        if (fullSseUrl == null || fullSseUrl.isBlank()) {
            throw new IllegalArgumentException("fullSseUrl 不能为空");
        }

        String url = fullSseUrl.trim();
        int sseIndex = url.indexOf("/sse");

        if (sseIndex < 0) {
            throw new IllegalArgumentException("fullSseUrl 必须包含 /sse，当前值：" + fullSseUrl);
        }

        String baseUri = url.substring(0, sseIndex);
        String sseEndpoint = url.substring(sseIndex);

        System.out.println("========== MCP SSE 地址解析 ==========");
        System.out.println("baseUri     = " + baseUri);
        System.out.println("sseEndpoint = " + sseEndpoint);

        HttpClientSseClientTransport transport = HttpClientSseClientTransport
                .builder(baseUri)
                .sseEndpoint(sseEndpoint)
                .build();

        return McpClient.sync(transport)
                .requestTimeout(Duration.ofMinutes(3))
                .build();
    }

    private String buildSearchText(String skillCode,
                                   String skillName,
                                   String description,
                                   String category,
                                   String tags,
                                   String serverCodes,
                                   String toolCodes,
                                   String toolNames) {
        return """
                skillCode: %s
                skillName: %s
                description: %s
                category: %s
                tags: %s
                serverCodes: %s
                toolCodes: %s
                toolNames: %s
                examples:
                - 帮我看一下百度热搜前十
                - 总结这个网页
                - 爬取这个链接的正文内容
                - 获取最新新闻或热搜信息
                """.formatted(
                nullToEmpty(skillCode),
                nullToEmpty(skillName),
                nullToEmpty(description),
                nullToEmpty(category),
                nullToEmpty(tags),
                nullToEmpty(serverCodes),
                nullToEmpty(toolCodes),
                nullToEmpty(toolNames)
        );
    }

    private List<Double> toDoubleList(float[] embedding) {
        List<Double> list = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            list.add((double) value);
        }
        return list;
    }

    private String extractJsonObject(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("LLM 返回为空");
        }

        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');

        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("没有找到 JSON 对象：" + raw);
        }

        return raw.substring(start, end + 1);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }

        String lowerText = text.toLowerCase();

        for (String keyword : keywords) {
            if (lowerText.contains(keyword.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        return Double.parseDouble(String.valueOf(value));
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCandidate {

        private String serverCode;

        private String fullSseUrl;

        private String skillCode;

        private String skillName;

        private String description;

        private String category;

        private String tags;

        private String searchText;

        private String toolCode;

        private String toolName;

        private double vectorScore;

        private double ruleScore;

        private double finalScore;

        private String rerankReason;
    }
}