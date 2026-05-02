package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * IntentRouterNode 只做 LLM Node 跳转，不承载工程能力。
 *
 * 改造点：
 * 1. 兼容 step1 的 meta/decompose/open_strategy 结果；
 * 2. 打印 subTasks 方便后续 step2 扩展；
 * 3. 维持原有 step2 接线不变。
 */
@Slf4j
@Service
public class IntentRouterNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ContractReviewNode contractReviewNode;
    @Resource
    private LegalQaNode legalQaNode;
    @Resource
    private CaseSearchNode caseSearchNode;
    @Resource
    private ComplianceCheckNode complianceCheckNode;
    @Resource
    private LegalDraftNode legalDraftNode;
    @Resource
    private GeneralChatNode generalChatNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        List<String> subTasks = context.getValue("sub_tasks");
        String answerMode = context.getValue("answer_mode");

        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                5,
                "IntentRouterNode：根据 intent 跳转业务 LLM Node："
                        + context.getIntentCode()
                        + (subTasks == null || subTasks.isEmpty() ? "" : " | subTasks=" + subTasks)
                        + (answerMode == null ? "" : " | answerMode=" + answerMode),
                context.getSessionId());

        log.info("IntentRouterNode route intent={} subTasks={} answerMode={}",
                context.getIntentCode(), subTasks, answerMode);

        return switch (context.getIntentCode()) {
            case "contract_review" -> contractReviewNode.apply(request, context);
            case "legal_qa" -> legalQaNode.apply(request, context);
            case "case_search" -> caseSearchNode.apply(request, context);
            case "compliance_check" -> complianceCheckNode.apply(request, context);
            case "document_drafting" -> legalDraftNode.apply(request, context);
            default -> generalChatNode.apply(request, context);
        };
    }
}
