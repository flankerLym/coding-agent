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
public class GeneralChatNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "GeneralChatNode"; }
    @Override protected String draftType() { return "general_chat"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.GENERAL_CHAT; }
    @Override protected String systemPrompt() {
        return """
                你是通用法律对话 Agent。用户问题不明确时优先提出澄清问题；能回答的一般性法律问题，给出谨慎、非正式说明。
                要求：不进行超出材料的复杂法律分析；不编造法条或案例。
                输出 JSON：{"draft_type":"general_chat","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"general_chat\",\"draft_answer\":\"我可以帮你做合同审查、法律问答、案例检索、合规检查或法律文书草拟。请补充你的具体问题或材料。\",\"key_findings\":[\"当前为通用法律对话 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"请补充具体法律事项、事实背景或材料。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "GeneralChatNode(openAiChatClient)：通用法律对话草稿生成完成。", context.getSessionId());
        log.info("GeneralChatNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
