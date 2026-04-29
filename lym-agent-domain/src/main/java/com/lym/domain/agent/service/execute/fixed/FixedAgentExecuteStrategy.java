package com.lym.domain.agent.service.execute.fixed;

import com.lym.domain.agent.adapter.repository.IAgentRepository;
import com.lym.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.model.valobj.AiAgentClientFlowConfigVO;
import com.lym.domain.agent.model.valobj.enums.AiAgentEnumVO;
import com.lym.domain.agent.service.IExecuteStrategy;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.List;

/**
 * 固定执行策略
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/9/13 15:14
 */
@Slf4j
@Service("fixedAgentExecuteStrategy")
public class FixedAgentExecuteStrategy implements IExecuteStrategy {

    @Resource
    private IAgentRepository repository;

    @Resource
    protected ApplicationContext applicationContext;

    public static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    public static final String CHAT_MEMORY_RETRIEVE_SIZE_KEY = "chat_memory_response_size";

    @Override
    public void execute(ExecuteCommandEntity requestParameter, ResponseBodyEmitter emitter) throws Exception {
        List<AiAgentClientFlowConfigVO> aiAgentClientList =
                repository.queryAiAgentClientsByAgentId(requestParameter.getAiAgentId());

        if (aiAgentClientList == null || aiAgentClientList.isEmpty()) {
            sendErrorResult(emitter,
                    "智能体未配置客户端流程，agentId=" + requestParameter.getAiAgentId(),
                    requestParameter.getSessionId());
            sendCompleteResult(emitter, requestParameter.getSessionId());
            return;
        }

        StringBuilder content = new StringBuilder();

        for (AiAgentClientFlowConfigVO config : aiAgentClientList) {
            if (config == null || config.getClientId() == null || config.getClientId().trim().isEmpty()) {
                sendErrorResult(emitter,
                        "智能体流程配置异常：clientId 为空，agentId=" + requestParameter.getAiAgentId(),
                        requestParameter.getSessionId());
                sendCompleteResult(emitter, requestParameter.getSessionId());
                return;
            }

            ChatClient chatClient = getChatClientByClientId(config.getClientId());

            String prompt = """
                用户输入：
                %s

                上一步结果：
                %s

                当前步骤要求：
                %s
                """.formatted(
                    requestParameter.getMessage(),
                    content,
                    config.getStepPrompt() == null ? "" : config.getStepPrompt()
            );

            StringBuilder stepContent = new StringBuilder();

            chatClient.prompt(prompt)
                    .system(s -> s.param("current_date", LocalDate.now().toString()))
                    .advisors(a -> a
                            .param(CHAT_MEMORY_CONVERSATION_ID_KEY, requestParameter.getSessionId())
                            .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 100))
                    .stream()
                    .content()
                    .doOnNext(delta -> {
                        try {
                            if (delta == null || delta.isBlank()) {
                                return;
                            }

                            stepContent.append(delta);

                            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.builder()
                                    .type("summary")
                                    .subType("stream")
                                    .step(config.getSequence())
                                    .content(delta)
                                    .completed(false)
                                    .timestamp(System.currentTimeMillis())
                                    .sessionId(requestParameter.getSessionId())
                                    .build();

                            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");

                        } catch (Exception e) {
                            log.error("发送流式片段失败：{}", e.getMessage(), e);
                        }
                    })
                    .blockLast();

            content = stepContent;

            log.info("智能体对话进行，agentId={}, clientId={}",
                    requestParameter.getAiAgentId(),
                    config.getClientId());
        }

        String finalContent = content.toString();

        log.info("智能体对话请求，结果 {} {}", requestParameter.getAiAgentId(), finalContent);

        if (!finalContent.trim().isEmpty()) {
            sendFinalResult(emitter, finalContent, requestParameter.getSessionId());
        } else {
            sendErrorResult(emitter, "模型返回为空", requestParameter.getSessionId());
        }

        sendCompleteResult(emitter, requestParameter.getSessionId());
    }

    private ChatClient getChatClientByClientId(String clientId) {
        return getBean(AiAgentEnumVO.AI_CLIENT.getBeanName(clientId));
    }

    private <T> T getBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
    
    /**
     * 发送最终结果到流式输出
     */
    private void sendFinalResult(ResponseBodyEmitter emitter, String content, String sessionId) {
        try {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createSummaryResult(content, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
            log.info("✅ 已发送最终结果");
        } catch (Exception e) {
            log.error("发送最终结果失败：{}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送完成标识到流式输出
     */
    private void sendCompleteResult(ResponseBodyEmitter emitter, String sessionId) {
        try {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createCompleteResult(sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
            log.info("✅ 已发送完成标识");
        } catch (Exception e) {
            log.error("发送完成标识失败：{}", e.getMessage(), e);
        }
    }
    private void sendErrorResult(ResponseBodyEmitter emitter, String content, String sessionId) {
        try {
            AutoAgentExecuteResultEntity result = AutoAgentExecuteResultEntity.createErrorResult(content, sessionId);
            String sseData = "data: " + JSON.toJSONString(result) + "\n\n";
            emitter.send(sseData);
            log.info("✅ 已发送错误结果");
        } catch (Exception e) {
            log.error("发送错误结果失败：{}", e.getMessage(), e);
        }
    }
}
