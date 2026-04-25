package com.lym.domain.agent.service.armory.node.factory.advisors;

import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.adapter.repository.ISkillRepository;
import com.lym.domain.agent.model.valobj.AiClientAdvisorVO;
import com.lym.domain.agent.model.valobj.RuntimeSkillVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Neo4j Skill Advisor。
 * 只注入 Skill 元数据，不直接执行 MCP Tool。
 */
public class Neo4jSkillAdvisor implements BaseAdvisor {

    private final ISkillRepository skillRepository;
    private final AiClientAdvisorVO.Neo4jSkill config;

    public Neo4jSkillAdvisor(ISkillRepository skillRepository, AiClientAdvisorVO.Neo4jSkill config) {
        this.skillRepository = skillRepository;
        this.config = config == null ? AiClientAdvisorVO.Neo4jSkill.builder().build() : config;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Map<String, Object> context = new HashMap<>(chatClientRequest.context());
        String userText = chatClientRequest.prompt().getUserMessage().getText();

        List<RuntimeSkillVO> skills = loadAndFilterSkills();
        context.put("neo4j_skill_advisor_skills", skills);

        String skillContext = buildSkillContext(skills);
        String advisedUserText = userText + System.lineSeparator() + System.lineSeparator()
                + "---------------------" + System.lineSeparator()
                + "Neo4j Skill Registry information is below. Use it only as capability metadata." + System.lineSeparator()
                + skillContext + System.lineSeparator()
                + "---------------------" + System.lineSeparator()
                + "Use these Skill descriptions to plan which capability is needed. Do not claim that a tool has been executed unless the execution result is provided.";

        Map<String, Object> advisedParams = new HashMap<>(context);
        advisedParams.put("neo4j_skill_context", skillContext);

        return ChatClientRequest.builder()
                .prompt(Prompt.builder()
                        .messages(new UserMessage(advisedUserText), new AssistantMessage(JSON.toJSONString(advisedParams)))
                        .build())
                .context(advisedParams)
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    private List<RuntimeSkillVO> loadAndFilterSkills() {
        List<RuntimeSkillVO> rows = skillRepository.queryEnabledMcpSkills();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, RuntimeSkillVO> uniqueSkillMap = new LinkedHashMap<>();
        for (RuntimeSkillVO row : rows) {
            if (StringUtils.isNotBlank(config.getCategory())
                    && !config.getCategory().equalsIgnoreCase(row.getCategory())) {
                continue;
            }
            uniqueSkillMap.putIfAbsent(row.getSkillCode(), row);
        }

        int maxSkills = config.getMaxSkills() <= 0 ? 10 : config.getMaxSkills();
        return uniqueSkillMap.values().stream().limit(maxSkills).collect(Collectors.toList());
    }

    private String buildSkillContext(List<RuntimeSkillVO> skills) {
        if (skills == null || skills.isEmpty()) {
            return "No enabled Skill was found in Neo4j.";
        }

        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (RuntimeSkillVO skill : skills) {
            builder.append(index++).append(". ")
                    .append("skillCode=").append(nullToEmpty(skill.getSkillCode()))
                    .append(", skillName=").append(nullToEmpty(skill.getSkillName()))
                    .append(", category=").append(nullToEmpty(skill.getCategory()))
                    .append(", skillType=").append(nullToEmpty(skill.getSkillType()))
                    .append(System.lineSeparator())
                    .append("   description=").append(nullToEmpty(skill.getDescription()))
                    .append(System.lineSeparator());

            if (config.isIncludeTools()) {
                builder.append("   relatedTool=").append(nullToEmpty(skill.getToolCode()))
                        .append(", server=").append(nullToEmpty(skill.getServerCode()))
                        .append(System.lineSeparator());
            }

            if (skill.getTags() != null && !skill.getTags().isEmpty()) {
                builder.append("   tags=").append(skill.getTags()).append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        return callAdvisorChain.nextCall(this.before(chatClientRequest, callAdvisorChain));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        return BaseAdvisor.super.adviseStream(this.before(chatClientRequest, streamAdvisorChain), streamAdvisorChain);
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
