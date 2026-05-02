package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.entity.AgentSessionEntity;
import com.lym.domain.agent.model.entity.AgentSessionQaRecordEntity;

import java.util.List;

public interface IAgentSessionRepository {
    String createSession(AgentSessionEntity session);

    List<AgentSessionEntity> querySessionListByUserId(String userId);

    AgentSessionEntity querySessionBySessionId(String sessionId);
    List<AgentSessionQaRecordEntity> queryMessageListBySessionId(String sessionId);

    String saveMessage(AgentSessionQaRecordEntity message);
}