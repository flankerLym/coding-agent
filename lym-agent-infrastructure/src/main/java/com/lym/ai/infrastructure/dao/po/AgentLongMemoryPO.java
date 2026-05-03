package com.lym.ai.infrastructure.dao.po;

import lombok.Data;

import java.util.Date;

@Data
public class AgentLongMemoryPO {

    private Long id;

    private String memoryId;

    private String userId;

    private String sessionId;

    private String recordId;

    private String intentCode;

    private String riskLevel;

    private String memorySummary;

    private Integer memoryStatus;

    private Integer vectorStatus;

    private String vectorError;

    private String extInfo;

    private Date createTime;

    private Date updateTime;
}