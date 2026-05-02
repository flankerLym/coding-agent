package com.lym.domain.agent.service.advisors;

import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.adapter.repository.ISkillRepository;
import com.lym.domain.agent.model.valobj.AiClientAdvisorVO;
import com.lym.domain.agent.model.valobj.RuntimeSkillVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP Tool Advisor。
 * 当前版本只注入工具元数据，不直接执行 MCP Tool。
 */
public class McpToolAdvisor implements BaseAdvisor {

    private final ISkillRepository skillRepository;
    private final AiClientAdvisorVO.McpTool config;

    public McpToolAdvisor(ISkillRepository skillRepository, AiClientAdvisorVO.McpTool config) {
        this.skillRepository = skillRepository;
        this.config = config == null ? AiClientAdvisorVO.McpTool.builder().build() : config;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        List<RuntimeSkillVO> tools = loadAndFilterTools();
        context.put("mcp_tool_advisor_tools", tools);

        String toolContext = buildToolContext(tools);
        String advisedUserText = userText + System.lineSeparator() + System.lineSeparator()
                + "---------------------" + System.lineSeparator()
                + "MCP Tool Registry information is below. Use it to decide which tool capability is needed." + System.lineSeparator()
                + toolContext + System.lineSeparator()
                + "---------------------" + System.lineSeparator()
                + "If tool execution is required, select the most relevant MCP tool capability. Do not fabricate execution results.";

        Map<String, Object> advisedParams = new HashMap<>(context);
        advisedParams.put("mcp_tool_context", toolContext);

        return ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(new UserMessage(advisedUserText), new AssistantMessage(JSON.toJSONString(advisedParams)))
                        .build())
                .context(advisedParams)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    private List<RuntimeSkillVO> loadAndFilterTools() {
        List<RuntimeSkillVO> rows = skillRepository.queryEnabledMcpSkills();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        int maxTools = config.getMaxTools() <= 0 ? 10 : config.getMaxTools();
        return rows.stream()
                .filter(row -> StringUtils.isBlank(config.getCategory()) || config.getCategory().equalsIgnoreCase(row.getCategory()))
                .filter(row -> StringUtils.isNotBlank(row.getToolCode()))
                .limit(maxTools)
                .collect(Collectors.toList());
    }

    private String buildToolContext(List<RuntimeSkillVO> tools) {
        if (tools == null || tools.isEmpty()) {
            return "No enabled MCP Tool was found in Neo4j.";
        }

        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RuntimeSkillVO tool : tools) {
            builder.append(index++).append(". ")
                    .append("toolCode=").append(nullToEmpty(tool.getToolCode()))
                    .append(", toolName=").append(nullToEmpty(tool.getToolName()))
                    .append(", invokeName=").append(nullToEmpty(tool.getInvokeName()))
                    .append(", toolType=").append(nullToEmpty(tool.getToolType()))
                    .append(System.lineSeparator())
                    .append("   skillCode=").append(nullToEmpty(tool.getSkillCode()))
                    .append(", skillName=").append(nullToEmpty(tool.getSkillName()))
                    .append(System.lineSeparator());

            if (config.isIncludeMcpServers()) {
                builder.append("   mcpServer=").append(nullToEmpty(tool.getServerCode()))
                        .append(", protocol=").append(nullToEmpty(tool.getProtocol()))
                        .append(", fullSseUrl=").append(maskSensitiveUrl(tool.getFullSseUrl()))
                        .append(System.lineSeparator());
            }
        }
        return builder.toString();
    }


    private String maskSensitiveUrl(String url) {
        if (StringUtils.isBlank(url)) return "";
        int queryIndex = url.indexOf('?');
        return queryIndex < 0 ? url : url.substring(0, queryIndex) + "?***";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(this.before(chatClientRequest, callAdvisorChain));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(this.before(chatClientRequest, streamAdvisorChain), streamAdvisorChain);
    }


    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
