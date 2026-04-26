package com.lym.agent.app.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 示例接口：给新版前端提供智能体选项。
 *
 * 你需要把 queryDragAgents() 和 queryBuiltinAgents() 替换为你项目真实的查询逻辑：
 * 1. dragAgents：拖拉拽编排出来的智能体
 * 2. builtinAgents：ai_agent 表 / 配置中写死的内置智能体
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin
public class AgentOptionsController {

    @GetMapping("/options")
    public Map<String, Object> options() {
        List<AgentOptionVO> dragAgents = queryDragAgents();
        List<AgentOptionVO> builtinAgents = queryBuiltinAgents();

        AgentOptionsResponse data = new AgentOptionsResponse(dragAgents, builtinAgents);

        Map<String, Object> result = new HashMap<>();
        result.put("code", "0000");
        result.put("info", "success");
        result.put("data", data);
        return result;
    }

    private List<AgentOptionVO> queryDragAgents() {
        // TODO 替换为拖拉拽智能体查询，比如从你的 agent client / workflow / graph 配置表中查。
        return List.of(
                new AgentOptionVO("101", "drag_agent_demo", "拖拉拽示例智能体", "来自拖拉拽编排的智能体", "🧩", "drag", 2)
        );
    }

    private List<AgentOptionVO> queryBuiltinAgents() {
        // TODO 替换为 ai_agent 查询。如果当前 ai_agent 是写死的，也可以先保留这里的静态列表。
        return List.of(
                new AgentOptionVO("1", "ai_agent_1", "自动自主规划", "CSDN 发帖 + 通知", "📄", "builtin", 2),
                new AgentOptionVO("3", "ai_agent_3", "智能对话分析", "通用智能对话分析", "💬", "builtin", 2),
                new AgentOptionVO("4", "ai_agent_4", "ELK日志检索分析", "日志检索、异常定位和总结", "📈", "builtin", 2),
                new AgentOptionVO("5", "ai_agent_5", "智能监控分析服务", "监控数据分析与告警诊断", "📊", "builtin", 2)
        );
    }
}
