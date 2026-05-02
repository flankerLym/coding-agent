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
public class AgentSessionPO {

    private Long id;

    private String sessionId;

    private String userId;

    private String title;

    private String initialQuestion;

    private Integer sessionStatus;

    private String extInfo;

    private Date createTime;

    private Date updateTime;

}