package com.lym.test.neo4j;

import com.lym.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Map;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class Neo4jClientTest {

    @Resource
    private Neo4jClient neo4jClient;

    @Test
    public void testNeo4jConnection() {
        Collection<Map<String, Object>> result = neo4jClient.query("""
                MATCH (m:McpServer {enabled: true})
                      -[:EXPOSES_SKILL]->(s:Skill {enabled: true})
                      -[:HAS_TOOL]->(t:Tool {enabled: true})
                RETURN
                    m.serverCode AS serverCode,
                    m.fullSseUrl AS fullSseUrl,
                    s.skillCode AS skillCode,
                    s.skillName AS skillName,
                    t.toolCode AS toolCode
                LIMIT 10
                """)
                .fetch()
                .all();

        result.forEach(System.out::println);
    }
}