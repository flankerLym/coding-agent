package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.entity.AgentMessageEntity;
import com.lym.domain.agent.model.entity.AgentSessionEntity;

/**
 * Agent 对话记录仓储接口。
 *
 * <p>Domain 层只定义会话/消息的保存能力，不直接依赖 MyBatis 或具体数据库实现，
 * 由 infrastructure 模块完成 agent_session、agent_message 两张表的落库。</p>
 */
public interface IAgentConversationRepository {

    /**
     * 保存或刷新会话记录。
     *
     * <p>只写入 agent_session 表；当同一 tenant + session 已存在时刷新当前意图、摘要、状态和更新时间。</p>
     *
     * @param agentSessionEntity 会话实体
     */
    void saveAgentSession(AgentSessionEntity agentSessionEntity);

    /**
     * 保存单条对话消息。
     *
     * <p>只写入 agent_message 表；用户提问由 Advisor.before 保存，模型回答由 Advisor.after 保存。</p>
     *
     * @param agentMessageEntity 消息实体
     */
    void saveAgentMessage(AgentMessageEntity agentMessageEntity);

}
