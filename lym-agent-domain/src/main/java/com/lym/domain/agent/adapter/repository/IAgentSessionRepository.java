package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.entity.AgentLongMemoryEntity;
import com.lym.domain.agent.model.entity.AgentSessionEntity;
import com.lym.domain.agent.model.entity.AgentSessionQaRecordEntity;

import java.util.List;

public interface IAgentSessionRepository {
    String createSession(AgentSessionEntity session);

    List<AgentSessionEntity> querySessionListByUserId(String userId);

    AgentSessionEntity querySessionBySessionId(String sessionId);
    List<AgentSessionQaRecordEntity> queryMessageListBySessionId(String sessionId);

    String saveMessage(AgentSessionQaRecordEntity message);

    List<String> queryShortMemory(String userId, String sessionId);

    void addShortMemory(String userId, String sessionId, String message);

    String saveLongMemory(AgentLongMemoryEntity longMemory);

    AgentLongMemoryEntity queryLongMemoryByMemoryId(String memoryId);

    void updateLongMemoryVectorStatus(String memoryId, Integer vectorStatus, String vectorError);

    void saveLongMemoryVector(AgentLongMemoryEntity agentLongMemoryEntity);

    List<String> queryLongMemory(String userId, String question, Integer topK);
}