package com.lym.domain.agent.service.execute.legalFlow.node.step3;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;

import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * MemorySummaryNode 仍然属于 LLM Node，因为它需要判断是否写入长期记忆并生成摘要。
 */
@Slf4j
@Service
public class MemorySummaryNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;


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

        String content = callLegalChatClient(ClientIdEnums.MEMORY_SUMMARY, systemPrompt, userPrompt, fallback);
        log.info("意图识别Node 执行完成，clientId:{} beanName:{} result:{}",
                ClientIdEnums.LEGAL_INTENT.getClientId(),
                ClientIdEnums.LEGAL_INTENT.getBeanName(),
                content == null ? null : content.substring(0, Math.min(content.length(), 500)));
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


        return "LEGAL_FLOW_SUCCESS";
    }

    @Override
    public String router(ExecuteCommandEntity request,
                         DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                         StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> next) throws Exception {
        return "LEGAL_FLOW_SUCCESS";
    }

}
