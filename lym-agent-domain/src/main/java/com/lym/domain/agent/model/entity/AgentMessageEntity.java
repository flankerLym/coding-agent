package com.lym.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 消息实体，对应 agent_message 表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentMessageEntity {

    /** 消息ID */
    private String messageId;

    /** 请求ID，同一次用户提问与模型回答保持一致 */
    private String requestId;

    /** 会话ID */
    private String sessionId;

    /** 租户ID，默认 default */
    private String tenantId;

    /** 用户ID */
    private String userId;

    /** 项目ID，可为空 */
    private String projectId;

    /** 角色：user/assistant/system/tool */
    private String roleType;

    /** 消息全文 */
    private String content;

    /** 消息摘要 */
    private String contentSummary;

    /** 法律任务类型/当前意图 */
    private String intent;

    /** 命中的 Skill */
    private String skillCode;

}
