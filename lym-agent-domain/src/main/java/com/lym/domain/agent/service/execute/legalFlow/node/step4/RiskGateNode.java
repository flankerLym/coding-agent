package com.lym.domain.agent.service.execute.legalFlow.node.step4;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step5.AnswerGenerateNode;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class RiskGateNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private AnswerGenerateNode answerGenerateNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律风险分级 Agent。
                根据用户问题、草稿答案和引用校验结果判断风险等级。
                输出 JSON：
                {"risk_level":"low|medium|high","risk_reason":"","need_human_review":false}
                如果 citation_status=fail，风险至少为 high。
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n草稿：\n" + JSON.toJSONString(context.getDraftResult())
                + "\n\n引用校验：\n"
                + JSON.toJSONString(context.getCitationStatus())
                + "\nunsupportedClaims：\n" + JSON.toJSONString(context.getUnsupportedClaims());

        String fallback = "{\"risk_level\":\"medium\",\"risk_reason\":\"涉及法律判断或合同权利义务，建议谨慎处理。\",\"need_human_review\":false}";
        String content = callOpenAiChatClient(applicationContext, systemPrompt, userPrompt, fallback);

        try {
            JSONObject jsonObject = JSON.parseObject(content);
            context.setRiskLevel(jsonObject.getString("risk_level"));
            context.setRiskReason(jsonObject.getString("risk_reason"));
            context.setNeedHumanReview(jsonObject.getBoolean("need_human_review"));
        } catch (Exception e) {
            context.setRiskLevel("medium");
            context.setRiskReason("风险分级解析失败，采用中风险保守处理。");
            context.setNeedHumanReview(false);
        }

        LegalFlowSseUtils.sendSupervision(context.getEmitter(), 7,
                "RiskGateNode(openAiChatClient)：riskLevel=" + context.getRiskLevel(),
                context.getSessionId());

        return router(request, context, get(request, context));
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return answerGenerateNode;
    }

}
