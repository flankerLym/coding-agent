package com.lym.domain.agent.service.execute.legalFlow;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.IExecuteStrategy;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * 法律助手执行策略。
 *
 * <p>核心范式：
 * 非 LLM 步骤全部封装成 Advisor；
 * 使用 openAiChatClient 的步骤才封装成策略树 Node。</p>
 */
@Slf4j
@Service("legalAgentExecuteStrategy")
public class legalAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private DefaultLegalFlowExecuteStrategyFactory defaultLegalFlowExecuteStrategyFactory;

    @Override
    public void execute(ExecuteCommandEntity executeCommandEntity, ResponseBodyEmitter emitter) throws Exception {
        StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> handler =
                defaultLegalFlowExecuteStrategyFactory.armoryStrategyHandler();

        DefaultLegalFlowExecuteStrategyFactory.DynamicContext dynamicContext =
                new DefaultLegalFlowExecuteStrategyFactory.DynamicContext();

        dynamicContext.setEmitter(emitter);
        dynamicContext.setMaxStep(executeCommandEntity.getMaxStep() != null ? executeCommandEntity.getMaxStep() : 6);
        dynamicContext.setCurrentTask(executeCommandEntity.getMessage());
        dynamicContext.setExecutionHistory(new StringBuilder());

        try {
            LegalFlowSseUtils.sendAnalysis(emitter, 0, "LegalFlow 启动：Advisor 增强 + LLM 策略树。", executeCommandEntity.getSessionId());
            String result = handler.apply(executeCommandEntity, dynamicContext);
            log.info("LegalFlow 执行完成 requestId:{} result:{}", dynamicContext.getRequestId(), result);

            LegalFlowSseUtils.sendSummary(emitter, dynamicContext.getFinalAnswer(), dynamicContext.getSessionId());
        } catch (Exception e) {
            log.error("LegalFlow 执行异常：{}", e.getMessage(), e);
            LegalFlowSseUtils.sendError(emitter, "LegalFlow 执行异常：" + e.getMessage(), executeCommandEntity.getSessionId());
            throw e;
        } finally {
            try {
                AutoAgentExecuteResultEntity completeResult =
                        AutoAgentExecuteResultEntity.createCompleteResult(
                                dynamicContext.getSessionId() != null ? dynamicContext.getSessionId() : executeCommandEntity.getSessionId());
                emitter.send("data: " + JSON.toJSONString(completeResult) + "\n\n");
            } catch (Exception e) {
                log.error("发送 LegalFlow 完成标识失败：{}", e.getMessage(), e);
            }
        }
    }

}
