package com.lym.domain.agent.service.execute.legalFlow.node.step2;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step3.ResultMergeNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class SubTaskRouterNode extends AbstractLegalLlmNodeSupport {

    @Resource private ContractReviewNode contractReviewNode;
    @Resource private LegalQaNode legalQaNode;
    @Resource private CaseSearchNode caseSearchNode;
    @Resource private ComplianceCheckNode complianceCheckNode;
    @Resource private LegalDraftNode legalDraftNode;
    @Resource private GeneralChatNode generalChatNode;
    @Resource private OpenStrategyExecutionNode openStrategyExecutionNode;
    @Resource private ResultMergeNode resultMergeNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        List<String> subTasks = context.getValue("sub_tasks");

        if (subTasks == null || subTasks.isEmpty()) {
            return routeSingle(request, context, context.getIntentCode());
        }

        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5,
                "SubTaskRouterNode：检测到复杂任务，开始执行子任务数量=" + subTasks.size(),
                context.getSessionId());

        context.setValue("agent_results", null);
        context.setValue("task_mode", "decompose");
        context.setValue("subtask_execution_mode", true);

        String originalIntent = context.getIntentCode();

        int index = 0;
        for (String subTask : subTasks) {
            index++;
            String safeSubTask = subTask == null ? "" : subTask.trim();
            if (safeSubTask.isEmpty()) {
                continue;
            }

            String subIntent = inferIntent(safeSubTask, originalIntent);
            context.setValue("current_sub_task", safeSubTask);
            context.setValue("current_sub_task_index", index);
            context.setIntentCode(subIntent);

            LegalFlowSseUtils.sendExecution(context.getEmitter(), 5,
                    "SubTaskRouterNode：执行子任务 " + index + "/" + subTasks.size()
                            + "，intent=" + subIntent + "，task=" + safeSubTask,
                    context.getSessionId());

            routeSingle(request, context, subIntent);
        }

        context.setValue("subtask_execution_mode", false);
        context.setValue("current_sub_task", null);
        context.setIntentCode(originalIntent);

        log.info("SubTaskRouterNode completed, subTasks={}", subTasks);
        return router(request, context, resultMergeNode);
    }

    private String routeSingle(ExecuteCommandEntity request,
                               DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                               String intent) throws Exception {
        String answerMode = context.getValue("answer_mode");
        String metaRoute = context.getValue("meta_route");

        if ("open_strategy".equals(answerMode) || "open_strategy".equals(metaRoute) || "open_strategy".equals(intent)) {
            return openStrategyExecutionNode.apply(request, context);
        }

        return switch (intent) {
            case "contract_review" -> contractReviewNode.apply(request, context);
            case "legal_qa" -> legalQaNode.apply(request, context);
            case "case_search" -> caseSearchNode.apply(request, context);
            case "compliance_check" -> complianceCheckNode.apply(request, context);
            case "document_drafting" -> legalDraftNode.apply(request, context);
            default -> generalChatNode.apply(request, context);
        };
    }

    private String inferIntent(String text, String fallbackIntent) {
        if (containsAny(text, "合同", "协议", "条款", "违约", "付款", "解除", "保密", "甲方", "乙方")) {
            return "contract_review";
        }
        if (containsAny(text, "案例", "判例", "类案", "裁判", "法院", "判决", "检索")) {
            return "case_search";
        }
        if (containsAny(text, "合规", "监管", "处罚", "资质", "数据安全", "隐私", "审计", "制度")) {
            return "compliance_check";
        }
        if (containsAny(text, "起草", "草拟", "帮我写", "模板", "律师函", "声明", "通知", "函件", "邮件")) {
            return "document_drafting";
        }
        if (containsAny(text, "方案", "设计", "规划", "体系", "架构", "流程", "怎么做", "如何搭建")) {
            return "open_strategy";
        }
        if (containsAny(text, "法律", "劳动", "赔偿", "仲裁", "诉讼", "公司", "股权", "离职", "辞退", "责任")) {
            return "legal_qa";
        }
        return fallbackIntent == null || fallbackIntent.trim().isEmpty() ? "general_chat" : fallbackIntent;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
