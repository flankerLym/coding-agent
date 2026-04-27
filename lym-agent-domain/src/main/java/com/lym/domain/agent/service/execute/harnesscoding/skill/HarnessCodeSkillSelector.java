package com.lym.domain.agent.service.execute.harnesscoding.skill;

import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeProjectSnapshot;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeSkill;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Skill 选择器。
 *
 * 当前是本地规则骨架；后续建议替换为：
 * 1. 从 Neo4j 查询 Skill 图谱；
 * 2. 根据 task + techStack + repoSnapshot 做召回；
 * 3. 把命中的 Skill 放入 Advisor，提供给模型规划。
 */
@Component
public class HarnessCodeSkillSelector {

    public HarnessCodeSkill select(HarnessCodeExecutionContext context, HarnessCodeProjectSnapshot snapshot) {
        String task = StringUtils.defaultString(context.getTask()).toLowerCase();
        String stack = snapshot.getTechStackSummary().toLowerCase();

        if (task.contains("spring ai") || task.contains("springai") || stack.contains("spring boot")) {
            return HarnessCodeSkill.builder()
                    .name("spring-boot-coding-skill")
                    .description("面向 Spring Boot / Spring AI 项目的代码分析、改造、测试验证技能")
                    .requiredTools(List.of("mcp.filesystem", "mcp.shell", "mcp.git", "mcp.maven"))
                    .verifyCommands(List.of("mvn test"))
                    .build();
        }

        if (stack.contains("node") || stack.contains("react")) {
            return HarnessCodeSkill.builder()
                    .name("frontend-coding-skill")
                    .description("面向 React / Node 项目的代码分析、改造、测试验证技能")
                    .requiredTools(List.of("mcp.filesystem", "mcp.shell", "mcp.npm"))
                    .verifyCommands(List.of("npm test", "npm run build"))
                    .build();
        }

        return HarnessCodeSkill.builder()
                .name("generic-coding-skill")
                .description("通用代码项目分析与改造技能")
                .requiredTools(List.of("mcp.filesystem", "mcp.shell", "mcp.git"))
                .verifyCommands(List.of("请根据项目类型补充测试命令"))
                .build();
    }
}
