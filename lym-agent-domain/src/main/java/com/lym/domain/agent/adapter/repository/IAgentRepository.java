package com.lym.domain.agent.adapter.repository;

import com.lym.domain.agent.model.valobj.*;

import java.util.List;
import java.util.Map;

/**
 * AiAgent 仓储接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 * 2025/6/27 16:48
 */
public interface IAgentRepository {


    List<AiClientApiVO> queryAiClientApiVOListByClientIds(List<String> clientIdList);

    List<AiClientModelVO> AiClientModelVOByClientIds(List<String> clientIdList);

    List<AiClientToolMcpVO> AiClientToolMcpVOByClientIds(List<String> clientIdList);

    List<AiClientSystemPromptVO> AiClientSystemPromptVOByClientIds(List<String> clientIdList);

    Map<String, AiClientSystemPromptVO> queryAiClientSystemPromptMapByClientIds(List<String> clientIdList);

    List<AiClientAdvisorVO> AiClientAdvisorVOByClientIds(List<String> clientIdList);

    List<AiClientVO> AiClientVOByClientIds(List<String> clientIdList);

    List<AiClientApiVO> queryAiClientApiVOListByModelIds(List<String> modelIdList);

    List<AiClientModelVO> AiClientModelVOByModelIds(List<String> modelIdList);

    Map<String, AiAgentClientFlowConfigVO> queryAiAgentClientFlowConfig(String aiAgentId);

    AiAgentVO queryAiAgentByAgentId(String aiAgentId);

/**
 * 根据AI代理ID查询相关的AI客户端流程配置信息
 *
 * @param aiAgentId AI代理ID，用于标识特定的AI代理
 * @return 返回一个AiAgentClientFlowConfigVO对象的列表，包含与指定AI代理ID相关的所有客户端流程配置信息
 */
    List<AiAgentClientFlowConfigVO> queryAiAgentClientsByAgentId(String aiAgentId);

    List<AiAgentTaskScheduleVO> queryAllValidTaskSchedule();

    List<Long> queryAllInvalidTaskScheduleIds();

    void createTagOrder(AiRagOrderVO aiRagOrderVO);

    /**
     * 查询可用的智能体列表
     * @return 可用的智能体列表
     */
    List<AiAgentVO> queryAvailableAgents();

}
