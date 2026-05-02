package com.lym.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Agent 消息 PO，对应 agent_message 表。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentMessage {

    /** 主键ID */
    private Long id;

    /** 消息ID */
    private String messageId;

    /** 请求ID */
    private String requestId;

    /** 会话ID */
    private String sessionId;

    /** 租户ID */
    private String tenantId;

    /** 用户ID */
    private String userId;

    /** 项目ID */
    private String projectId;

    /** 角色：user/assistant/system/tool */
    private String roleType;

    /** 消息内容 */
    private String content;

    /** 消息摘要 */
    private String contentSummary;

    /** 法律任务类型 */
    private String intent;

    /** 命中的 Skill */
    private String skillCode;

    /** 创建时间 */
    private LocalDateTime createTime;

}
