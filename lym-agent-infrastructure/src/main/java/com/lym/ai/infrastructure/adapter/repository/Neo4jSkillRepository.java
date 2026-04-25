package com.lym.ai.infrastructure.adapter.repository;

import com.lym.domain.agent.adapter.repository.ISkillRepository;
import com.lym.domain.agent.model.valobj.RuntimeSkillVO;
import jakarta.annotation.Resource;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Neo4j Skill 仓储实现。
 */
@Repository
public class Neo4jSkillRepository implements ISkillRepository {

    @Resource
    private Neo4jClient neo4jClient;

    @Override
    public List<RuntimeSkillVO> queryEnabledMcpSkills() {
        String cypher = """
                MATCH (m:McpServer {enabled: true})-[:EXPOSES_SKILL]->(s:Skill {enabled: true})-[:HAS_TOOL]->(t:Tool {enabled: true})
                RETURN m.serverCode AS serverCode,
                       m.serverName AS serverName,
                       m.protocol AS protocol,
                       m.baseUrl AS baseUrl,
                       m.sseEndpoint AS sseEndpoint,
                       m.fullSseUrl AS fullSseUrl,
                       m.requestTimeout AS requestTimeout,
                       s.skillCode AS skillCode,
                       s.skillName AS skillName,
                       s.description AS description,
                       s.skillType AS skillType,
                       s.category AS category,
                       s.tags AS tags,
                       t.toolCode AS toolCode,
                       t.toolName AS toolName,
                       t.invokeName AS invokeName,
                       t.toolType AS toolType,
                       t.autoDiscovery AS autoDiscovery
                ORDER BY s.skillCode, t.toolCode
                """;

        return neo4jClient.query(cypher).fetch().all().stream().map(this::toRuntimeSkillVO).toList();
    }

    private RuntimeSkillVO toRuntimeSkillVO(Map<String, Object> row) {
        return RuntimeSkillVO.builder()
                .serverCode(asString(row.get("serverCode")))
                .serverName(asString(row.get("serverName")))
                .protocol(asString(row.get("protocol")))
                .baseUrl(asString(row.get("baseUrl")))
                .sseEndpoint(asString(row.get("sseEndpoint")))
                .fullSseUrl(asString(row.get("fullSseUrl")))
                .requestTimeout(asInteger(row.get("requestTimeout")))
                .skillCode(asString(row.get("skillCode")))
                .skillName(asString(row.get("skillName")))
                .description(asString(row.get("description")))
                .skillType(asString(row.get("skillType")))
                .category(asString(row.get("category")))
                .tags(asStringList(row.get("tags")))
                .toolCode(asString(row.get("toolCode")))
                .toolName(asString(row.get("toolName")))
                .invokeName(asString(row.get("invokeName")))
                .toolType(asString(row.get("toolType")))
                .autoDiscovery(asBoolean(row.get("autoDiscovery")))
                .build();
    }

    private String asString(Object value) { return value == null ? null : String.valueOf(value); }

    private Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private Boolean asBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<String> asStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof Iterable<?> iterable) {
            List<String> result = new ArrayList<>();
            for (Object item : iterable) if (item != null) result.add(String.valueOf(item));
            return result;
        }
        return List.of(String.valueOf(value));
    }
}
