package com.lym.ai.infrastructure.dao.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentSessionQaRecordPO {

    private Long id;

    private String recordId;

    private String sessionId;

    private Integer sequenceNo;

    private String userQuestion;

    private String agentAnswer;

    private String agentId;

    private Integer recordStatus;

    private String extInfo;

    private Date createTime;

    private Date updateTime;

}