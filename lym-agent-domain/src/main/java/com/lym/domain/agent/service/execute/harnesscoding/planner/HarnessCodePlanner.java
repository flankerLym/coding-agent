package com.lym.domain.agent.service.execute.harnesscoding.planner;

import com.lym.domain.agent.service.execute.harnesscoding.model.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Coding 计划生成器。
 *
 * 当前是规则骨架；后续可以替换为：
 * ChatClient + Advisor(skillFromNeo4j, projectSnapshot, codingPolicy) 生成计划。
 */
@Component
public class HarnessCodePlanner {

    public HarnessCodePlan generatePlan(HarnessCodeExecutionContext context,
                                        HarnessCodeProjectSnapshot snapshot,
                                        HarnessCodeSkill skill) {
        List<HarnessCodeStep> steps = new ArrayList<>();

        steps.add(HarnessCodeStep.builder()
                .title("扫描项目结构")
                .instruction("读取项目目录、关键配置文件、识别语言和框架")
                .toolName("mcp.filesystem.scan")
                .expectedOutput("项目结构摘要、技术栈、关键文件列表")
                .build());

        steps.add(HarnessCodeStep.builder()
                .title("理解用户改造目标")
                .instruction("结合 task、README、配置文件，判断需要修改的模块和风险")
                .toolName("advisor.reasoning")
                .expectedOutput("需求拆解、影响范围、风险点")
                .build());

        steps.add(HarnessCodeStep.builder()
                .title("生成代码改造方案")
                .instruction("基于 Skill 和项目上下文，生成文件级修改建议")
                .toolName("advisor.plan_patch")
                .expectedOutput("待新增/修改文件清单与修改说明")
                .build());

        steps.add(HarnessCodeStep.builder()
                .title("执行代码修改占位")
                .instruction("后续在这里调用 MCP filesystem/git 工具写入 patch；当前版本只输出建议，不直接改代码")
                .toolName("mcp.filesystem.patch")
                .expectedOutput("diff patch 或文件变更记录")
                .build());

        steps.add(HarnessCodeStep.builder()
                .title("验证与回归测试")
                .instruction("根据项目类型生成验证命令；若打开 enable-command-execute，则执行测试")
                .toolName("mcp.shell.verify")
                .expectedOutput("测试命令、测试日志、失败修复建议")
                .build());

        return HarnessCodePlan.builder().steps(steps).build();
    }
}
