package com.lym.domain.agent.service.execute.harnesscoding.tool;

import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeStep;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeToolResult;
import org.springframework.stereotype.Component;

/**
 * MCP 工具执行器占位。
 *
 * 后续你接 Neo4j + Advisor + MCP 时，建议在这里完成：
 * 1. 根据 step.toolName 从 Neo4j 查询 Tool 定义；
 * 2. 把 Tool 定义注入 Advisor 或 ToolCallback；
 * 3. 调用 MCP filesystem / shell / git / maven / npm；
 * 4. 收集 tool call 日志、diff、stdout/stderr；
 * 5. 返回 HarnessCodeToolResult。
 */
@Component
public class HarnessCodeMcpToolExecutor {

    public HarnessCodeToolResult execute(HarnessCodeExecutionContext context, HarnessCodeStep step) {
        String toolName = step.getToolName();

        // TODO: 后续替换为真实 MCP 调用，例如：
        // McpSyncClient mcpClient = getBean(AiAgentEnumVO.AI_CLIENT_TOOL_MCP.getBeanName(toolId));
        // CallToolResult result = mcpClient.callTool(new CallToolRequest(toolName, args));
        // return convert(result);

        String detail = "[MCP占位] tool=" + toolName + "\n"
                + "workspace=" + context.getWorkspace().getWorkspaceDir() + "\n"
                + "instruction=" + step.getInstruction() + "\n"
                + "说明：当前骨架不直接修改用户代码，后续请在这里接入 Neo4j Advisor + MCP 工具体系。";

        return HarnessCodeToolResult.builder()
                .toolName(toolName)
                .success(true)
                .summary("工具调用占位完成：" + toolName)
                .detail(detail)
                .build();
    }
}
