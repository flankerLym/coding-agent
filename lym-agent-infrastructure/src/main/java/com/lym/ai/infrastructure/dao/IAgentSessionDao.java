package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.AgentSessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IAgentSessionDao {

    int insert(AgentSessionPO sessionPO);

    List<AgentSessionPO> queryByUserId(@Param("userId") String userId);

    AgentSessionPO queryBySessionId(@Param("sessionId") String sessionId);

    String lockBySessionId(@Param("sessionId") String sessionId);

}