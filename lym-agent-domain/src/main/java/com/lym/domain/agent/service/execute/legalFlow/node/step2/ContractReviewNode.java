package com.lym.domain.agent.service.execute.legalFlow.node.step2;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step3.CitationVerifyNode;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class ContractReviewNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private CitationVerifyNode citationVerifyNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是合同审查 Agent。基于用户问题、最近上下文、Skill路径、MCP工具和检索材料，识别合同风险并生成草稿。不得编造合同条款或法律依据。输出 JSON：{"draft_type":"contract_review","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext())
                + "\n\nSkill路径：\n" + JSON.toJSONString(context.getSkillPath())
                + "\n\nMCP工具：\n" + JSON.toJSONString(context.getMcpTools())
                + "\n\n检索材料：\n" + JSON.toJSONString(context.getMemoryHits());

        String fallback = "{\"draft_type\":\"contract_review\",\"draft_answer\":\"合同审查草稿：建议重点关注付款、违约责任、解除条款、争议解决、保密和知识产权条款。\",\"key_findings\":[\"当前为合同审查 Agent草稿。\"],\"risk_points\":[],\"missing_info\":[\"如需更准确分析，请补充完整材料。\"]}";
        String content = callOpenAiChatClient(applicationContext, systemPrompt, userPrompt, fallback);

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
                    .draftType("contract_review")
                    .draftAnswer("合同审查草稿：建议重点关注付款、违约责任、解除条款、争议解决、保密和知识产权条款。")
                    .keyFindings(new ArrayList<>())
                    .riskPoints(new ArrayList<>())
                    .missingInfo(new ArrayList<>())
                    .build();
        }

        context.setDraftResult(draftResult);
        context.addTrace("ContractReviewNode(openAiChatClient)：生成草稿：" + draftResult.getDraftAnswer());

        LegalFlowSseUtils.sendExecution(context.getEmitter(), 5,
                "ContractReviewNode(openAiChatClient)：草稿生成完成。",
                context.getSessionId());

        return router(request, context, get(request, context));
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return citationVerifyNode;
    }

}
