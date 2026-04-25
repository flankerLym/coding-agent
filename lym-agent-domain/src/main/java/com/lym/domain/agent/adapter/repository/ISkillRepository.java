package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.valobj.RuntimeSkillVO;

import java.util.List;

/**
 * Skill 仓储接口。
 * Domain 层只定义接口，Infrastructure 层使用 Neo4j 实现。
 */
public interface ISkillRepository {

    /**
     * 查询 Neo4j 中所有启用的 MCP Skill / Tool / Server 元数据。
     */
    List<RuntimeSkillVO> queryEnabledMcpSkills();

}
