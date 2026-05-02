package com.lym.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 会话实体，对应 agent_session 表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentSessionEntity {

    /** 会话ID */
    private String sessionId;

    /** 租户ID，默认 default */
    private String tenantId;

    /** 用户ID */
    private String userId;

    /** 项目ID，可为空 */
    private String projectId;

    /** 智能体ID，对应 ai_agent.agent_id */
    private String agentId;

    /** 当前意图 */
    private String currentIntent;

    /** 会话摘要 */
    private String contextSummary;

    /** 状态：0关闭，1正常 */
    private Integer status;

}
