package com.lym.domain.agent.service.execute.harnesscoding;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;

import com.lym.domain.agent.service.IExecuteStrategy;
import com.lym.domain.agent.service.execute.harnesscoding.context.HarnessCodeContextBuilder;
import com.lym.domain.agent.service.execute.harnesscoding.event.HarnessCodeEventPublisher;
import com.lym.domain.agent.service.execute.harnesscoding.model.*;
import com.lym.domain.agent.service.execute.harnesscoding.planner.HarnessCodePlanner;
import com.lym.domain.agent.service.execute.harnesscoding.reporter.HarnessCodeReporter;
import com.lym.domain.agent.service.execute.harnesscoding.skill.HarnessCodeSkillSelector;
import com.lym.domain.agent.service.execute.harnesscoding.tool.HarnessCodeMcpToolExecutor;
import com.lym.domain.agent.service.execute.harnesscoding.verifier.HarnessCodeVerifier;
import com.lym.domain.agent.service.execute.harnesscoding.workspace.HarnessCodeProjectScanner;
import com.lym.domain.agent.service.execute.harnesscoding.workspace.HarnessCodeWorkspaceManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

/**
 * HarnessCode 执行策略。
 *
 * 设计目标：和 auto / flow / fix 策略并行，作为 Coding Agent 的受控执行运行时。
 * Bean 名必须和数据库 ai_agent.strategy 一致，例如：strategy = harnesscode。
 */
@Slf4j
@Service("harnesscode")
public class HarnessCodeExecuteStrategy implements IExecuteStrategy {

    @Resource
    private HarnessCodeContextBuilder contextBuilder;

    @Resource
    private HarnessCodeWorkspaceManager workspaceManager;

    @Resource
    private HarnessCodeProjectScanner projectScanner;

    @Resource
    private HarnessCodeSkillSelector skillSelector;

    @Resource
    private HarnessCodePlanner planner;

    @Resource
    private HarnessCodeMcpToolExecutor toolExecutor;

    @Resource
    private HarnessCodeVerifier verifier;

    @Resource
    private HarnessCodeReporter reporter;

    @Resource
    private HarnessCodeEventPublisher eventPublisher;

    @Override
    public void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        HarnessCodeExecutionContext context = contextBuilder.build(requestParameter);

        eventPublisher.publish(emitter, HarnessCodeEvent.analysis("收到 Coding Harness 任务，开始解析执行上下文"));
        log.info("HarnessCode execute start, sessionId={}, agentId={}", context.getSessionId(), context.getAiAgentId());

        HarnessCodeWorkspace workspace = workspaceManager.prepareWorkspace(context);
        context.setWorkspace(workspace);
        eventPublisher.publish(emitter, HarnessCodeEvent.context("Workspace 已创建：" + workspace.getWorkspaceDir()));

        HarnessCodeProjectSnapshot snapshot = projectScanner.scan(workspace.getProjectRoot());
        context.setSnapshot(snapshot);
        eventPublisher.publish(emitter, HarnessCodeEvent.context("项目扫描完成，识别技术栈：" + snapshot.getTechStackSummary()));

        HarnessCodeSkill skill = skillSelector.select(context, snapshot);
        context.setSelectedSkill(skill);
        eventPublisher.publish(emitter, HarnessCodeEvent.skill("命中 Coding Skill：" + skill.getName()));

        HarnessCodePlan plan = planner.generatePlan(context, snapshot, skill);
        context.setPlan(plan);
        eventPublisher.publish(emitter, HarnessCodeEvent.plan(plan.toDisplayText()));

        int maxStep = context.getMaxStep() == null || context.getMaxStep() <= 0 ? 5 : context.getMaxStep();
        List<HarnessCodeStep> steps = plan.getSteps();
        int limit = Math.min(maxStep, steps.size());

        for (int i = 0; i < limit; i++) {
            HarnessCodeStep step = steps.get(i);
            eventPublisher.publish(emitter, HarnessCodeEvent.execution("开始执行 Step " + (i + 1) + "：" + step.getTitle()));

            HarnessCodeToolResult result = toolExecutor.execute(context, step);
            context.getToolResults().add(result);

            if (result.isSuccess()) {
                eventPublisher.publish(emitter, HarnessCodeEvent.execution("Step " + (i + 1) + " 完成：" + result.getSummary()));
            } else {
                eventPublisher.publish(emitter, HarnessCodeEvent.error("Step " + (i + 1) + " 失败：" + result.getSummary()));
                break;
            }
        }

        HarnessCodeVerifyResult verifyResult = verifier.verify(context);
        context.setVerifyResult(verifyResult);
        eventPublisher.publish(emitter, HarnessCodeEvent.verify(verifyResult.toDisplayText()));

        HarnessCodeReport report = reporter.buildReport(context);
        eventPublisher.publish(emitter, HarnessCodeEvent.summary(report.toMarkdown()));
        eventPublisher.publish(emitter, HarnessCodeEvent.done("HarnessCode 执行完成"));
    }
}
