package com.lym.domain.agent.service.execute.legalFlow.node.step2.business;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.LegalBusinessNodeSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContractReviewNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "ContractReviewNode"; }
    @Override protected String draftType() { return "contract_review"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.CONTRACT_REVIEW; }
    @Override protected String systemPrompt() {
        return """
                你是合同审查 Agent。基于用户问题、当前子任务、最近上下文、Skill路径、MCP工具和检索材料，识别合同风险并生成结构化草稿。
                要求：不得编造合同条款、事实或法律依据；必须区分已知事实和需要补充的信息；重点关注付款、违约责任、解除、争议解决、保密、知识产权、管辖、期限、交付验收等。
                输出 JSON：{"draft_type":"contract_review","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"contract_review\",\"draft_answer\":\"合同审查草稿：建议重点关注付款、违约责任、解除条款、争议解决、保密和知识产权条款。\",\"key_findings\":[\"当前为合同审查 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"如需更准确分析，请补充完整合同文本。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "ContractReviewNode(openAiChatClient)：合同审查草稿生成完成。", context.getSessionId());
        log.info("ContractReviewNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
