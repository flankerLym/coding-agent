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
public class ComplianceCheckNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "ComplianceCheckNode"; }
    @Override protected String draftType() { return "compliance_check"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.COMPLIANCE_CHECK; }
    @Override protected String systemPrompt() {
        return """
                你是合规检查 Agent。基于用户问题、上下文、Skill路径和检索材料识别合规风险。
                要求：区分已确认事实和假设事实；从监管要求、主体资质、数据安全、业务流程、合同管理、留痕审计等角度检查。
                输出 JSON：{"draft_type":"compliance_check","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"compliance_check\",\"draft_answer\":\"合规检查草稿：建议从监管要求、主体资质、业务流程、合同管理和数据安全五方面审查。\",\"key_findings\":[\"当前为合规检查 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"请补充业务场景、主体资质、适用地域和相关制度材料。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "ComplianceCheckNode(openAiChatClient)：合规检查草稿生成完成。", context.getSessionId());
        log.info("ComplianceCheckNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
