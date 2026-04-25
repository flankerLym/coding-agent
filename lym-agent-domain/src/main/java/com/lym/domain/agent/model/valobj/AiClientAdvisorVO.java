package com.lym.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 顾问配置，值对象
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 18:42
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientAdvisorVO {

    /**
     * 顾问ID
     */
    private String advisorId;

    /**
     * 顾问名称
     */
    private String advisorName;

    /**
     * 顾问类型(PromptChatMemory/RagAnswer/SimpleLoggerAdvisor等)
     */
    private String advisorType;

    /**
     * 顺序号
     */
    private Integer orderNum;

    /**
     * 扩展；记忆
     */
    private ChatMemory chatMemory;

    /**
     * 扩展；rag 问答
     */
    private RagAnswer ragAnswer;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatMemory {
        private int maxMessages;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RagAnswer {
        private int topK = 4;
        private String filterExpression;
    }
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Neo4jSkill {

        /**
         * Skill 编码
         */
        private String skillCode;

        /**
         * Skill 名称
         */
        private String skillName;

        /**
         * Skill 描述
         */
        private String skillDescription;

        /**
         * Skill 内容
         */
        private String skillContent;

        /**
         * Skill 分类
         */
        private String category;

        /**
         * MCP 地址
         */
        private String mcpUrl;

        /**
         * Cypher 查询语句，可选
         */
        private String cypher;

        /**
         * 查询关键词，可选
         */
        private String query;

        /**
         * 最多加载多少个 Skill
         */
        @Builder.Default
        private int maxSkills = 10;

        /**
         * 是否把 Skill 关联的工具信息也注入 Prompt
         *
         * 注意：这里必须用 boolean，而不是 Boolean。
         * 这样 Lombok 才会生成 isIncludeTools()。
         */
        @Builder.Default
        private boolean includeTools = true;

        /**
         * 是否启用
         */
        @Builder.Default
        private boolean enabled = true;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class McpTool {

        /**
         * 最多注入多少个工具
         */
        @Builder.Default
        private int maxTools = 10;

        /**
         * 工具分类，可选
         */
        private String category;

        /**
         * 是否把 MCP Server 信息也注入 Prompt
         */
        @Builder.Default
        private boolean includeMcpServers = true;
    }
}
