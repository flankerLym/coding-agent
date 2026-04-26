package com.lym.agent.app.controller;

import java.util.List;

public class AgentOptionsResponse {
    private List<AgentOptionVO> dragAgents;
    private List<AgentOptionVO> builtinAgents;

    public AgentOptionsResponse() {
    }

    public AgentOptionsResponse(List<AgentOptionVO> dragAgents, List<AgentOptionVO> builtinAgents) {
        this.dragAgents = dragAgents;
        this.builtinAgents = builtinAgents;
    }

    public List<AgentOptionVO> getDragAgents() { return dragAgents; }
    public void setDragAgents(List<AgentOptionVO> dragAgents) { this.dragAgents = dragAgents; }
    public List<AgentOptionVO> getBuiltinAgents() { return builtinAgents; }
    public void setBuiltinAgents(List<AgentOptionVO> builtinAgents) { this.builtinAgents = builtinAgents; }
}
