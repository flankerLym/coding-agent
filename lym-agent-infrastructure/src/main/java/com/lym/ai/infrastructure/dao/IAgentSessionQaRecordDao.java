package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.AgentSessionQaRecordPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IAgentSessionQaRecordDao {

    int insert(AgentSessionQaRecordPO recordPO);

    Integer queryMaxSequenceNo(@Param("sessionId") String sessionId);

    List<AgentSessionQaRecordPO> queryBySessionId(@Param("sessionId") String sessionId);

}