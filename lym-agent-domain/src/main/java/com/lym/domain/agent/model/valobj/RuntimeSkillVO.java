package com.lym.domain.agent.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Neo4j Skill Runtime 视图对象。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RuntimeSkillVO {

    private String serverCode;
    private String serverName;
    private String protocol;
    private String baseUrl;
    private String sseEndpoint;
    private String fullSseUrl;
    private Integer requestTimeout;

    private String skillCode;
    private String skillName;
    private String description;
    private String skillType;
    private String category;
    private List<String> tags;

    private String toolCode;
    private String toolName;
    private String invokeName;
    private String toolType;
    private Boolean autoDiscovery;

}
