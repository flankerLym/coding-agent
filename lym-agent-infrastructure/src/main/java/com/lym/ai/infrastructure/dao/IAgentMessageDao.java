package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.AgentMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 消息 DAO，只操作 agent_message 表。
 */
@Mapper
public interface IAgentMessageDao {

    /**
     * 新增单条消息。
     *
     * @param agentMessage 消息 PO
     * @return 影响行数
     */
    int insert(AgentMessage agentMessage);

}
