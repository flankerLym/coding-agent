package com.lym.domain.agent.service.execute.legalFlow.node.step2;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step3.CitationVerifyNode;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class LegalQaNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private CitationVerifyNode citationVerifyNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律问答 Agent。基于检索材料和上下文生成法律问答草稿。不得给绝对结论，不得编造法条。输出 JSON：{"draft_type":"legal_qa","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext())
                + "\n\nSkill路径：\n" + JSON.toJSONString(context.getSkillPath())
                + "\n\nMCP工具：\n" + JSON.toJSONString(context.getMcpTools())
                + "\n\n检索材料：\n" + JSON.toJSONString(context.getMemoryHits());

        String fallback = "{\"draft_type\":\"legal_qa\",\"draft_answer\":\"法律问答草稿：可基于现有事实给出一般规则、适用条件和下一步建议。\",\"key_findings\":[\"当前为法律问答 Agent草稿。\"],\"risk_points\":[],\"missing_info\":[\"如需更准确分析，请补充完整材料。\"]}";
        String content = callLegalChatClient(ClientIdEnums.LEGAL_QA, systemPrompt, userPrompt, fallback);
        LegalDraftResult draftResult;
        try {
            JSONObject jsonObject = JSON.parseObject(content);
            draftResult = LegalDraftResult.builder()
                    .draftType(jsonObject.getString("draft_type"))
                    .draftAnswer(jsonObject.getString("draft_answer"))
                    .keyFindings(jsonObject.getJSONArray("key_findings") == null ? new ArrayList<>() : jsonObject.getJSONArray("key_findings").toJavaList(String.class))
                    .riskPoints(jsonObject.getJSONArray("risk_points") == null ? new ArrayList<>() : jsonObject.getJSONArray("risk_points").toJavaList(String.class))
                    .missingInfo(jsonObject.getJSONArray("missing_info") == null ? new ArrayList<>() : jsonObject.getJSONArray("missing_info").toJavaList(String.class))
                    .build();
        } catch (Exception e) {
            draftResult = LegalDraftResult.builder()
                    .draftType("legal_qa")
                    .draftAnswer("法律问答草稿：可基于现有事实给出一般规则、适用条件和下一步建议。")
                    .keyFindings(new ArrayList<>())
                    .riskPoints(new ArrayList<>())
                    .missingInfo(new ArrayList<>())
                    .build();
        }

        context.setDraftResult(draftResult);
        context.addTrace("LegalQaNode(openAiChatClient)：生成草稿：" + draftResult.getDraftAnswer());

        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5,
                "LegalQaNode(openAiChatClient)：草稿生成完成。",
                context.getSessionId());

        return router(request, context, get(request, context));
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return citationVerifyNode;
    }

}
