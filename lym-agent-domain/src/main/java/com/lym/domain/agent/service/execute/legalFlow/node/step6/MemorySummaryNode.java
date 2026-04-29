package com.lym.domain.agent.service.execute.legalFlow.node.step6;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.armory.node.factory.advisors.LegalFlowAdvisorChain;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * MemorySummaryNode 仍然属于 LLM Node，因为它需要判断是否写入长期记忆并生成摘要。
 */
@Service
public class MemorySummaryNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private LegalFlowAdvisorChain legalFlowAdvisorChain;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律助手记忆摘要 Agent。
                判断本轮问答是否值得写入长期记忆，并生成脱敏摘要。
                不要保存身份证号、手机号、完整地址等敏感隐私。
                输出 JSON：
                {"should_save":true,"summary":""}
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最终答案：\n" + context.getFinalAnswer()
                + "\n\n风险等级：\n" + context.getRiskLevel();

        String fallback = "{\"should_save\":" + (!"general_chat".equals(context.getIntentCode())) + ",\"summary\":\"用户问题类型："
                + context.getIntentCode() + "；风险等级：" + context.getRiskLevel()
                + "；本轮完成法律分析、引用校验和风险分级。\"}";

        String content = callOpenAiChatClient(applicationContext, systemPrompt, userPrompt, fallback);

        try {
            JSONObject jsonObject = JSON.parseObject(content);
            context.setShouldSaveMemory(jsonObject.getBoolean("should_save"));
            context.setMemorySummary(jsonObject.getString("summary"));
        } catch (Exception e) {
            context.setShouldSaveMemory(!"general_chat".equals(context.getIntentCode()));
            context.setMemorySummary("用户问题类型：" + context.getIntentCode()
                    + "；风险等级：" + context.getRiskLevel()
                    + "；本轮完成法律分析、引用校验和风险分级。");
        }

        LegalFlowSseUtils.sendExecution(context.getEmitter(), 9,
                "MemorySummaryNode(openAiChatClient)：摘要记忆生成完成，shouldSave=" + context.getShouldSaveMemory(),
                context.getSessionId());

        // LLM 节点结束后，执行后置持久化 Advisor
        legalFlowAdvisorChain.afterAnswer(request, context);

        return "LEGAL_FLOW_SUCCESS";
    }

    @Override
    public String router(ExecuteCommandEntity request,
                         DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                         StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> next) throws Exception {
        return "LEGAL_FLOW_SUCCESS";
    }

}
