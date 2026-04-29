package com.lym.domain.agent.service.execute.legalFlow.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;

/**
 * 法律助手 LLM 策略树节点基类。
 *
 * <p>只有需要 openAiChatClient 的步骤才继承这个类。
 * 如果容器中没有名为 openAiChatClient 的 ChatClient，会自动走 fallback，方便第一阶段先跑通。</p>
 */
@Slf4j
public abstract class AbstractLegalLlmNodeSupport implements
        StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> {

    protected String router(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                            StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> next) throws Exception {
        if (next == null) {
            return "LEGAL_FLOW_END";
        }
        return next.apply(request, context);
    }

    protected String callOpenAiChatClient(ApplicationContext applicationContext,
                                          String systemPrompt,
                                          String userPrompt,
                                          String fallback) {
        try {
            ChatClient openAiChatClient = applicationContext.getBean("openAiChatClient", ChatClient.class);
            String content = openAiChatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null || content.trim().isEmpty()) {
                return fallback;
            }
            return content;
        } catch (Exception e) {
            log.warn("openAiChatClient 不可用，使用 fallback。原因：{}", e.getMessage());
            return fallback;
        }
    }

}
