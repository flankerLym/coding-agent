package com.lym.domain.agent.service.execute.legalFlow.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.model.valobj.enums.AiAgentEnumVO;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;

/**
 * 法律助手 LLM 策略树节点基类。
 *
 * 模仿 auto/step/AbstractExecuteSupport 的客户端获取方式：
 *
 * clientId -> AiAgentEnumVO.AI_CLIENT.getBeanName(clientId) -> Spring Bean
 */
@Slf4j
public abstract class AbstractLegalLlmNodeSupport implements
        StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> {

    @Resource
    protected ApplicationContext applicationContext;

    protected String router(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                            StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> next) throws Exception {
        if (next == null) {
            return "LEGAL_FLOW_END";
        }
        return next.apply(request, context);
    }

    /**
     * 完全模仿 auto 中的 getChatClientByClientId。
     */
    protected ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    /**
     * 枚举方式获取 ChatClient。
     */
    protected ChatClient getChatClientByClientId(ClientIdEnums clientIdEnums) {
        return getChatClientByClientId(clientIdEnums.getClientId());
    }

    @SuppressWarnings("unchecked")
    protected <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }

    /**
     * 统一调用 LegalFlow 的动态 ChatClient。
     */
    protected String callLegalChatClient(ClientIdEnums clientIdEnums,
                                         String systemPrompt,
                                         String userPrompt,
                                         String fallback) {
        String clientId = clientIdEnums.getClientId();
        String beanName = clientIdEnums.getBeanName();

        try {
            ChatClient chatClient = getChatClientByClientId(clientIdEnums);

            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            if (content == null || content.trim().isEmpty()) {
                return fallback;
            }

            return content;
        } catch (Exception e) {
            log.warn("LegalFlow ChatClient 不可用，node:{} clientId:{} beanName:{}，使用 fallback。原因：{}",
                    clientIdEnums.getNodeName(), clientId, beanName, e.getMessage());
            return fallback;
        }
    }
}