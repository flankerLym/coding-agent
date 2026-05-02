package com.lym.domain.agent.service.execute.legalFlow.node.step2;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LegalQaNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "LegalQaNode"; }
    @Override protected String draftType() { return "legal_qa"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.LEGAL_QA; }
    @Override protected String systemPrompt() {
        return """
                你是法律问答 Agent。基于检索材料和上下文生成法律问答草稿。
                要求：不作绝对结论；不编造法条、案例或事实；按规则/适用条件/风险/下一步建议组织。
                输出 JSON：{"draft_type":"legal_qa","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"legal_qa\",\"draft_answer\":\"法律问答草稿：可基于现有事实给出一般规则、适用条件和下一步建议。\",\"key_findings\":[\"当前为法律问答 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"如需更准确分析，请补充事实经过和相关材料。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "LegalQaNode(openAiChatClient)：法律问答草稿生成完成。", context.getSessionId());
        log.info("LegalQaNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
