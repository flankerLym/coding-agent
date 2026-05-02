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
public class OpenStrategyExecutionNode extends LegalBusinessNodeSupport {
    @Override protected String nodeName() { return "OpenStrategyExecutionNode"; }
    @Override protected String draftType() { return "open_strategy"; }
    @Override protected ClientIdEnums clientIdEnums() { return ClientIdEnums.GENERAL_CHAT; }
    @Override protected String systemPrompt() {
        return """
                你是开放性方案执行 Agent。处理法律系统设计、合规体系建设、风险治理方案、复杂开放式咨询等问题。
                要求：先给总体方案，再拆成模块、流程、数据、风控、落地步骤；不把开放性建议伪装成正式法律意见。
                输出 JSON：{"draft_type":"open_strategy","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }
    @Override protected String fallbackJson() {
        return "{\"draft_type\":\"open_strategy\",\"draft_answer\":\"开放性方案草稿：建议从目标定义、业务流程、知识库/RAG、工具编排、风险控制、审计留痕和持续评估几个层面设计。\",\"key_findings\":[\"当前为开放性方案执行 Agent 草稿。\"],\"risk_points\":[\"开放性方案需要结合具体业务、地域和监管要求进一步校准。\"],\"missing_info\":[\"请补充具体业务场景、目标用户、数据来源、合规边界和上线约束。\"]}";
    }
    @Override public String apply(ExecuteCommandEntity request, DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5, "OpenStrategyExecutionNode(openAiChatClient)：开放性方案草稿生成完成。", context.getSessionId());
        log.info("OpenStrategyExecutionNode completed, draftType={}, answer={}", draftResult.getDraftType(), draftResult.getDraftAnswer());
        return afterDraft(request, context, draftResult);
    }
}
