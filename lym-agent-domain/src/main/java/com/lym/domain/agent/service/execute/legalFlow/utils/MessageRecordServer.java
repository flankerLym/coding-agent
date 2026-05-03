package com.lym.domain.agent.service.execute.legalFlow.utils;
import java.util.UUID;
import com.lym.domain.agent.adapter.repository.IAgentSessionRepository;
import com.lym.domain.agent.model.entity.AgentSessionEntity;
import com.lym.domain.agent.model.entity.AgentSessionQaRecordEntity;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageRecordServer {

    private final IAgentSessionRepository agentSessionRepository;

    /**
     * 记录一轮问答：
     * 1. 有 sessionId：直接保存消息
     * 2. 没有 sessionId：先创建 session，再保存消息
     */
    public void recordMessage(ExecuteCommandEntity request,
                                DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                                String answer) {

        String sessionId = request.getSessionId();

        if (sessionId == null || sessionId.isBlank()) {
            sessionId = generateSessionId();
            agentSessionRepository.createSession(
                    AgentSessionEntity.builder()
                            .sessionId(sessionId)
                            .userId(context.getUserId())
                            .title(context.getValue("meta_title"))
                            .initialQuestion(request.getMessage())
                            .sessionStatus(0)
                            .build()
            );
        }
        String recordId = generateRecordId();
        agentSessionRepository.saveMessage(
                AgentSessionQaRecordEntity.builder()
                        .recordId(recordId)
                        .sessionId(sessionId)
                        .agentId(request.getAiAgentId())
                        .userQuestion(request.getMessage())
                        .agentAnswer(answer)
                        .recordStatus(1)
                        .build()
        );
        request.setSessionId(sessionId);
        context.setRecordId(recordId);

    }
    private String generateSessionId() {
        return "S" + UUID.randomUUID().toString().replace("-", "");
    }
    private String generateRecordId() {
        return "Mg" + UUID.randomUUID().toString().replace("-", "");
    }
}