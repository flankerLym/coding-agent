package com.lym.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 会话 PO，对应 agent_session 表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentSession {

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 租户ID */
    private String tenantId;

    /** 用户ID */
    private String userId;

    /** 项目ID */
    private String projectId;

    /** 智能体ID，对应 ai_agent.agent_id */
    private String agentId;

    /** 当前意图 */
    private String currentIntent;

    /** 会话摘要 */
    private String contextSummary;

    /** 状态：0关闭，1正常 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
