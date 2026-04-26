package com.lym.agent.app.controller;

public class AgentOptionVO {
    private String id;
    private String agentCode;
    private String agentName;
    private String description;
    private String avatar;
    /** drag 或 builtin */
    private String category;
    private Integer maxStep;

    public AgentOptionVO() {
    }

    public AgentOptionVO(String id, String agentCode, String agentName, String description, String avatar, String category, Integer maxStep) {
        this.id = id;
        this.agentCode = agentCode;
        this.agentName = agentName;
        this.description = description;
        this.avatar = avatar;
        this.category = category;
        this.maxStep = maxStep;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getMaxStep() { return maxStep; }
    public void setMaxStep(Integer maxStep) { this.maxStep = maxStep; }
}
