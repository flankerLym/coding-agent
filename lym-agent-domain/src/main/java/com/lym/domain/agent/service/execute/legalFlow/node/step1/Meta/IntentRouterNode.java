package com.lym.domain.agent.service.execute.legalFlow.node.step1.Meta;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.*;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.business.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class IntentRouterNode extends AbstractLegalLlmNodeSupport {

    @Resource private ContractReviewNode contractReviewNode;
    @Resource private LegalQaNode legalQaNode;
    @Resource private CaseSearchNode caseSearchNode;
    @Resource private ComplianceCheckNode complianceCheckNode;
    @Resource private LegalDraftNode legalDraftNode;
    @Resource private GeneralChatNode generalChatNode;
    @Resource private OpenStrategyExecutionNode openStrategyExecutionNode;
    @Resource private SubTaskRouterNode subTaskRouterNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        List<String> subTasks = context.getValue("sub_tasks");
        String answerMode = context.getValue("answer_mode");
        String metaRoute = context.getValue("meta_route");

        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                5,
                "IntentRouterNode：intent=" + context.getIntentCode()
                        + (subTasks == null || subTasks.isEmpty() ? "" : " | subTasks=" + subTasks.size())
                        + (answerMode == null ? "" : " | answerMode=" + answerMode),
                context.getSessionId());

        log.info("IntentRouterNode route intent={} subTasks={} answerMode={}",
                context.getIntentCode(), subTasks, answerMode);

        if (subTasks != null && !subTasks.isEmpty()) {
            return subTaskRouterNode.apply(request, context);
        }

        if ("open_strategy".equals(answerMode) || "open_strategy".equals(metaRoute)) {
            return openStrategyExecutionNode.apply(request, context);
        }

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
