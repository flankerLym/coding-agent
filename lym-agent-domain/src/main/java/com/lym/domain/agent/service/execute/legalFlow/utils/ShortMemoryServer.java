package com.lym.domain.agent.service.execute.legalFlow.utils;

import com.lym.domain.agent.adapter.repository.IAgentSessionRepository;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ShortMemoryServer {

    private static final String EMPTY_CONTEXT = "无最近上下文";

    private final IAgentSessionRepository agentSessionRepository;

    public List<String> getShortContext(ExecuteCommandEntity request,
                                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {

        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return Collections.singletonList(EMPTY_CONTEXT);
        }

        return agentSessionRepository.queryShortMemory(
                context.getUserId(),
                request.getSessionId()
        );
    }

    public void addShortMessage(ExecuteCommandEntity request,
                                DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {

        if (request.getSessionId() == null || request.getSessionId().isBlank()) {
            return;
        }

        agentSessionRepository.addShortMemory(
                context.getUserId(),
                request.getSessionId(),
                request.getMessage()
        );
    }
}