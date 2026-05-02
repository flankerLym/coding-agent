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
public class LegalDraftNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "LegalDraftNode"; }
    @Override protected String draftType() { return "document_drafting"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.LEGAL_DRAFT; }
    @Override protected String systemPrompt() {
        return """
                你是法律文书草拟 Agent。根据用户需求生成法律文书草稿。
                要求：使用【甲方】、【乙方】、【金额】、【日期】等占位符；明确标记为草稿；缺失信息写入 missing_info。
                输出 JSON：{"draft_type":"document_drafting","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"document_drafting\",\"draft_answer\":\"文书草拟草稿：建议使用【甲方】、【乙方】、【金额】、【日期】等占位符生成初稿，并在正式使用前由专业人士复核。\",\"key_findings\":[\"当前为法律文书草拟 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"请补充主体信息、事实背景、金额、期限、管辖和签署日期。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "LegalDraftNode(openAiChatClient)：法律文书草稿生成完成。", context.getSessionId());
        log.info("LegalDraftNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
