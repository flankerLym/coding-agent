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
public class AgentSessionQaRecordEntity {

    private Long id;

    private String recordId;

    private String sessionId;

    private Integer sequenceNo;

    private String userQuestion;

    private String agentAnswer;

    private String agentId;

    /**
     * 0-已保存问题，1-已保存回答，2-完整问答
     */
    private Integer recordStatus;

    private String extInfo;

    private Date createTime;

    private Date updateTime;

}