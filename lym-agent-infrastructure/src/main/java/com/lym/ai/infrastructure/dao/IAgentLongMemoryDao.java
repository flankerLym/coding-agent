package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.AgentLongMemoryPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IAgentLongMemoryDao {

    int insert(AgentLongMemoryPO longMemoryPO);

    AgentLongMemoryPO queryByMemoryId(@Param("memoryId") String memoryId);

    int updateVectorStatus(@Param("memoryId") String memoryId,
                           @Param("vectorStatus") Integer vectorStatus,
                           @Param("vectorError") String vectorError);
}