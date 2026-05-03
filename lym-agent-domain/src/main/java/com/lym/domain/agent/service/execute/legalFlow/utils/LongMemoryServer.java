package com.lym.domain.agent.service.execute.legalFlow.utils;

import com.lym.domain.agent.adapter.repository.IAgentSessionRepository;
import com.lym.domain.agent.model.entity.AgentLongMemoryEntity;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LongMemoryServer {

    private final IAgentSessionRepository agentSessionRepository;

    /**
     * 编排长期记忆保存流程：
     * 1. 判断是否需要保存
     * 2. 保存 MySQL 长期记忆主表
     * 3. 写入 pgvector
     * 4. 更新 vector_status
     */
    public String saveLongMemory(ExecuteCommandEntity request,
                                 DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {

        if (!Boolean.TRUE.equals(context.getShouldSaveMemory())) {
            return null;
        }

        if (context.getMemorySummary() == null || context.getMemorySummary().isBlank()) {
            return null;
        }

        String userId = context.getUserId();


        if (userId == null || userId.isBlank()) {
            log.warn("长期记忆保存跳过，userId 为空");
            return null;
        }

        String sessionId = request.getSessionId();
        if ((sessionId == null || sessionId.isBlank()) && context.getSessionId() != null) {
            sessionId = context.getSessionId();
        }
        AgentLongMemoryEntity agentLongMemoryEntity = AgentLongMemoryEntity.builder()
                .userId(userId)
                .sessionId(sessionId)
                .recordId(context.getRecordId())
                .intentCode(context.getIntentCode())
                .riskLevel(context.getRiskLevel())
                .memorySummary(context.getMemorySummary())
                .memoryStatus(1)
                .vectorStatus(0)
                .build();
        String memoryId = agentSessionRepository.saveLongMemory(
               agentLongMemoryEntity
        );
        agentLongMemoryEntity.setMemoryId(memoryId);
        try {
            agentSessionRepository.saveLongMemoryVector(agentLongMemoryEntity);
            agentSessionRepository.updateLongMemoryVectorStatus(memoryId, 1, null);
        } catch (Exception e) {
            log.error("长期记忆向量写入失败，memoryId: {}", memoryId, e);
            agentSessionRepository.updateLongMemoryVectorStatus(memoryId, 2, e.getMessage());
        }

        context.setMemoryId(memoryId);

        return memoryId;
    }

    public void acquireLongMemory(ExecuteCommandEntity request,
                                  DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {

        String userId = context.getUserId();

        if (userId == null || userId.isBlank()) {
            context.setValue("long_memory_context", "无长期记忆");
            return;
        }

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            context.setValue("long_memory_context", "无长期记忆");
            return;
        }

        List<String> longMemoryList = agentSessionRepository.queryLongMemory(
                userId,
                request.getMessage(),
                5
        );

        if (longMemoryList == null || longMemoryList.isEmpty()) {
            context.setValue("long_memory_context", "无长期记忆");
            return;
        }

        String longMemoryContext = String.join("\n", longMemoryList);

        context.setValue("long_memory_context", longMemoryContext);
    }
}