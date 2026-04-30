package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * IntentRouterNode 只做 LLM Node 跳转，不承载工程能力。
 */
@Slf4j
@Service
public class IntentRouterNode implements StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> {

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
        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 4,
                "IntentRouterNode：根据 intent 跳转业务 LLM Node：" + context.getIntentCode(),
                context.getSessionId());
        log.info("意图识别route 执行完成，result:{}", context.getIntentCode());
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
