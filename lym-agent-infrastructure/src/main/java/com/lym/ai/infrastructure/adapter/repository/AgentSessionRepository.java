package com.lym.ai.infrastructure.adapter.repository;

import com.lym.ai.infrastructure.dao.IAgentSessionDao;
import com.lym.ai.infrastructure.dao.IAgentSessionQaRecordDao;
import com.lym.ai.infrastructure.dao.po.AgentSessionPO;
import com.lym.ai.infrastructure.dao.po.AgentSessionQaRecordPO;
import com.lym.domain.agent.adapter.repository.IAgentSessionRepository;
import com.lym.domain.agent.model.entity.AgentSessionEntity;
import com.lym.domain.agent.model.entity.AgentSessionQaRecordEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AgentSessionRepository implements IAgentSessionRepository {

    private static final int SHORT_MEMORY_SIZE = 10;
    private static final String EMPTY_CONTEXT = "无最近上下文";
    private static final String SHORT_MEMORY_KEY_PREFIX = "legalflow:short_memory:";

    private final IAgentSessionDao agentSessionDao;
    private final StringRedisTemplate stringRedisTemplate;
    private final IAgentSessionQaRecordDao agentSessionQaRecordDao;

    @Override
    public String createSession(AgentSessionEntity session) {
        String sessionId = session.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = generateSessionId();
        }

        Date now = new Date();

        AgentSessionPO po = new AgentSessionPO();
        po.setSessionId(sessionId);
        po.setUserId(session.getUserId());
        po.setTitle(session.getTitle());
        po.setInitialQuestion(session.getInitialQuestion());
        po.setSessionStatus(session.getSessionStatus() == null ? 0 : session.getSessionStatus());
        po.setExtInfo(session.getExtInfo());
        po.setCreateTime(now);
        po.setUpdateTime(now);

        agentSessionDao.insert(po);
        return sessionId;
    }

    @Override
    public List<AgentSessionEntity> querySessionListByUserId(String userId) {
        return agentSessionDao.queryByUserId(userId)
                .stream()
                .map(this::convertSession)
                .toList();
    }

    @Override
    public AgentSessionEntity querySessionBySessionId(String sessionId) {
        AgentSessionPO po = agentSessionDao.queryBySessionId(sessionId);
        return po == null ? null : convertSession(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String saveMessage(AgentSessionQaRecordEntity message) {
        String sessionId = message.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        String lockedSessionId = agentSessionDao.lockBySessionId(sessionId);
        if (lockedSessionId == null) {
            throw new IllegalArgumentException("会话不存在，sessionId = " + sessionId);
        }

        Integer maxSequenceNo = agentSessionQaRecordDao.queryMaxSequenceNo(sessionId);
        int nextSequenceNo = maxSequenceNo == null ? 0 : maxSequenceNo + 1;

        String recordId = message.getRecordId();
        if (recordId == null || recordId.isBlank()) {
            recordId = generateRecordId();
        }

        Date now = new Date();

        AgentSessionQaRecordPO po = new AgentSessionQaRecordPO();
        po.setRecordId(recordId);
        po.setSessionId(sessionId);
        po.setSequenceNo(nextSequenceNo);
        po.setUserQuestion(message.getUserQuestion());
        po.setAgentAnswer(message.getAgentAnswer());
        po.setAgentId(message.getAgentId());
        po.setRecordStatus(message.getRecordStatus() == null ? 2 : message.getRecordStatus());
        po.setExtInfo(message.getExtInfo());
        po.setCreateTime(now);
        po.setUpdateTime(now);

        agentSessionQaRecordDao.insert(po);
        return recordId;
    }

    @Override
    public List<AgentSessionQaRecordEntity> queryMessageListBySessionId(String sessionId) {
        return agentSessionQaRecordDao.queryBySessionId(sessionId)
                .stream()
                .map(this::convertRecord)
                .toList();
    }

    private AgentSessionEntity convertSession(AgentSessionPO po) {
        return AgentSessionEntity.builder()
                .id(po.getId())
                .sessionId(po.getSessionId())
                .userId(po.getUserId())
                .title(po.getTitle())
                .sessionStatus(po.getSessionStatus())
                .extInfo(po.getExtInfo())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    private AgentSessionQaRecordEntity convertRecord(AgentSessionQaRecordPO po) {
        return AgentSessionQaRecordEntity.builder()
                .id(po.getId())
                .recordId(po.getRecordId())
                .sessionId(po.getSessionId())
                .sequenceNo(po.getSequenceNo())
                .userQuestion(po.getUserQuestion())
                .agentAnswer(po.getAgentAnswer())
                .agentId(po.getAgentId())
                .recordStatus(po.getRecordStatus())
                .extInfo(po.getExtInfo())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }

    @Override
    public List<String> queryShortMemory(String userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return List.of(EMPTY_CONTEXT);
        }

        String redisKey = buildShortMemoryKey(userId, sessionId);

        List<String> memoryList = stringRedisTemplate.opsForList().range(redisKey, 0, -1);
        if (memoryList != null && !memoryList.isEmpty()) {
            return memoryList;
        }

        List<String> recentQuestions = agentSessionQaRecordDao.queryRecentUserQuestions(sessionId);
        if (recentQuestions == null || recentQuestions.isEmpty()) {
            return List.of(EMPTY_CONTEXT);
        }

        stringRedisTemplate.opsForList().rightPushAll(redisKey, recentQuestions);
        stringRedisTemplate.opsForList().trim(redisKey, -SHORT_MEMORY_SIZE, -1);

        return recentQuestions;
    }

    @Override
    public void addShortMemory(String userId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        if (message == null || message.isBlank()) {
            return;
        }

        String redisKey = buildShortMemoryKey(userId, sessionId);

        stringRedisTemplate.opsForList().rightPush(redisKey, message);
        stringRedisTemplate.opsForList().trim(redisKey, -SHORT_MEMORY_SIZE, -1);
    }

    private String buildShortMemoryKey(String userId, String sessionId) {
        return SHORT_MEMORY_KEY_PREFIX + userId + ":" + sessionId;
    }

    private String buildMemoryText(AgentSessionQaRecordEntity record) {
        StringBuilder builder = new StringBuilder();

        if (record.getUserQuestion() != null && !record.getUserQuestion().isBlank()) {
            builder.append("用户：").append(record.getUserQuestion());
        }

        if (record.getAgentAnswer() != null && !record.getAgentAnswer().isBlank()) {
            if (!builder.isEmpty()) {
                builder.append("\n");
            }
            builder.append("助手：").append(record.getAgentAnswer());
        }

        return builder.toString();
    }
    private String generateSessionId() {
        return "S" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String generateRecordId() {
        return "R" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }


}