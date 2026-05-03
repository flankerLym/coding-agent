package com.lym.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLongMemoryEntity {

    private Long id;

    private String memoryId;

    private String userId;

    private String sessionId;

    private String recordId;

    private String intentCode;

    private String riskLevel;

    private String memorySummary;

    /**
     * 1-有效，0-无效
     */
    private Integer memoryStatus;

    /**
     * 0-待向量化，1-已完成，2-失败
     */
    private Integer vectorStatus;

    private String vectorError;

    private String extInfo;

    private Date createTime;

    private Date updateTime;
}