package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.AgentSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Agent 会话 DAO，只操作 agent_session 表。
 */
@Mapper
public interface IAgentSessionDao {

    /**
     * 新增会话。
     *
     * @param agentSession 会话 PO
     * @return 影响行数
     */
    int insert(AgentSession agentSession);

    /**
     * 根据 tenant_id + session_id 查询会话。
     *
     * @param tenantId 租户ID
     * @param sessionId 会话ID
     * @return 会话 PO
     */
    AgentSession queryByTenantIdAndSessionId(@Param("tenantId") String tenantId, @Param("sessionId") String sessionId);

    /**
     * 根据 tenant_id + session_id 更新会话摘要、当前意图、状态和更新时间。
     *
     * @param agentSession 会话 PO
     * @return 影响行数
     */
    int updateByTenantIdAndSessionId(AgentSession agentSession);

}
