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
public class CaseSearchNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "CaseSearchNode"; }
    @Override protected String draftType() { return "case_search"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.CASE_SEARCH; }
    @Override protected String systemPrompt() {
        return """
                你是案例检索 Agent。根据检索材料整理类案观点和裁判规则。
                要求：不得编造案号、法院、日期、裁判结果；材料不足时必须说明不足以支持具体类案结论。
                输出 JSON：{"draft_type":"case_search","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"case_search\",\"draft_answer\":\"案例检索草稿：应根据争议焦点检索相似案例，并整理裁判观点和适用边界。\",\"key_findings\":[\"当前为案例检索 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"请补充争议焦点、地域、法院层级或时间范围。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "CaseSearchNode(openAiChatClient)：案例检索草稿生成完成。", context.getSessionId());
        log.info("CaseSearchNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
