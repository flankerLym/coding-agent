package com.lym.test.dao;

import com.lym.ai.infrastructure.adapter.repository.AgentSessionRepository;
import com.lym.config.DataSourceConfig;
import com.lym.domain.agent.adapter.repository.IAgentSessionRepository;
import com.lym.domain.agent.model.entity.AgentSessionEntity;
import com.lym.domain.agent.model.entity.AgentSessionQaRecordEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class AgentSessionRepositoryTest {

    @Autowired
    private IAgentSessionRepository agentSessionRepository;

    @Test
    public void test_create_session_query_sessions_save_messages_query_messages() {
        String userId = "user_001";
        String agentId = "agent_001";

        String sessionId = agentSessionRepository.createSession(
                AgentSessionEntity.builder()
                        .userId(userId)
                        .title("测试会话")
                        .initialQuestion("初始问题")
                        .build()
        );

        Assert.assertNotNull(sessionId);

        List<AgentSessionEntity> sessions = agentSessionRepository.querySessionListByUserId(userId);
        Assert.assertEquals(1, sessions.size());
        Assert.assertEquals(sessionId, sessions.get(0).getSessionId());
        Assert.assertEquals(userId, sessions.get(0).getUserId());

        agentSessionRepository.saveMessage(
                AgentSessionQaRecordEntity.builder()
                        .sessionId(sessionId)
                        .userQuestion("第一个问题")
                        .agentAnswer("第一个回答")
                        .agentId(agentId)
                        .build()
        );

        agentSessionRepository.saveMessage(
                AgentSessionQaRecordEntity.builder()
                        .sessionId(sessionId)
                        .userQuestion("第二个问题")
                        .agentAnswer("第二个回答")
                        .agentId(agentId)
                        .build()
        );

        agentSessionRepository.saveMessage(
                AgentSessionQaRecordEntity.builder()
                        .sessionId(sessionId)
                        .userQuestion("第三个问题")
                        .agentAnswer("第三个回答")
                        .agentId(agentId)
                        .build()
        );

        List<AgentSessionQaRecordEntity> messages =
                agentSessionRepository.queryMessageListBySessionId(sessionId);

        Assert.assertEquals(3, messages.size());

        Assert.assertEquals(Integer.valueOf(0), messages.get(0).getSequenceNo());
        Assert.assertEquals("第一个问题", messages.get(0).getUserQuestion());
        Assert.assertEquals("第一个回答", messages.get(0).getAgentAnswer());

        Assert.assertEquals(Integer.valueOf(1), messages.get(1).getSequenceNo());
        Assert.assertEquals("第二个问题", messages.get(1).getUserQuestion());
        Assert.assertEquals("第二个回答", messages.get(1).getAgentAnswer());

        Assert.assertEquals(Integer.valueOf(2), messages.get(2).getSequenceNo());
        Assert.assertEquals("第三个问题", messages.get(2).getUserQuestion());
        Assert.assertEquals("第三个回答", messages.get(2).getAgentAnswer());
    }

}