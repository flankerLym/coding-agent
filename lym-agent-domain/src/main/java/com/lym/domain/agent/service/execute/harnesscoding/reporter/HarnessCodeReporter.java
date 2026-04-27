package com.lym.domain.agent.service.execute.harnesscoding.reporter;

import com.lym.domain.agent.service.execute.harnesscoding.model.*;
import org.springframework.stereotype.Component;

@Component
public class HarnessCodeReporter {

    public HarnessCodeReport buildReport(HarnessCodeExecutionContext context) {
        StringBuilder md = new StringBuilder();
        md.append("# HarnessCode 执行报告\n\n");
        md.append("## 1. 用户任务\n\n").append(context.getTask()).append("\n\n");

        md.append("## 2. 项目信息\n\n");
        if (context.getSnapshot() != null) {
            md.append("- ProjectRoot: `").append(context.getSnapshot().getProjectRoot()).append("`\n");
            md.append("- TechStack: ").append(context.getSnapshot().getTechStackSummary()).append("\n");
            md.append("- ImportantFiles:\n");
            for (String file : context.getSnapshot().getImportantFiles()) {
                md.append("  - `").append(file).append("`\n");
            }
        }

        md.append("\n## 3. 命中 Skill\n\n");
        if (context.getSelectedSkill() != null) {
            md.append("- ").append(context.getSelectedSkill().getName()).append("\n");
            md.append("- ").append(context.getSelectedSkill().getDescription()).append("\n");
        }

        md.append("\n## 4. 执行计划\n\n");
        if (context.getPlan() != null) {
            md.append(context.getPlan().toDisplayText()).append("\n");
        }

        md.append("\n## 5. 工具调用结果\n\n");
        for (HarnessCodeToolResult result : context.getToolResults()) {
            md.append("- `").append(result.getToolName()).append("`: ")
                    .append(result.isSuccess() ? "成功" : "失败")
                    .append("，").append(result.getSummary()).append("\n");
        }

        md.append("\n## 6. 验证结果\n\n");
        if (context.getVerifyResult() != null) {
            md.append(context.getVerifyResult().toDisplayText()).append("\n");
        }

        md.append("\n## 7. 后续替换点\n\n");
        md.append("- 将 `HarnessCodeSkillSelector` 替换为 Neo4j Skill 图谱查询。\n");
        md.append("- 将 `HarnessCodeMcpToolExecutor` 替换为真实 MCP filesystem/shell/git 调用。\n");
        md.append("- 将 `HarnessCodePlanner` 替换为 ChatClient + Advisor 生成计划。\n");
        md.append("- 将 `HarnessCodeReporter` 接入 diff、测试报告、artifact 持久化。\n");

        return HarnessCodeReport.builder().markdown(md.toString()).build();
    }
}
