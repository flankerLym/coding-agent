package com.lym.domain.agent.service.execute.harnesscoding.context;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class HarnessCodeContextBuilder {

    public HarnessCodeExecutionContext build(ExecuteCommandEntity request) {
        String rawMessage = StringUtils.defaultString(request.getMessage());
        Map<String, String> directives = parseDirectives(rawMessage);
        String sessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString().replace("-", ""));
        String task = firstNotBlank(directives.get("task"), removeDirectiveLines(rawMessage), rawMessage);

        return HarnessCodeExecutionContext.builder()
                .aiAgentId(request.getAiAgentId())
                .sessionId(sessionId)
                .rawMessage(rawMessage)
                .task(task)
                .maxStep(request.getMaxStep())
                .zipPath(directives.get("zipPath"))
                .projectPath(directives.get("projectPath"))
                .gitUrl(directives.get("gitUrl"))
                .branch(directives.get("branch"))
                .build();
    }

    private Map<String, String> parseDirectives(String message) {
        Map<String, String> map = new LinkedHashMap<>();
        String[] lines = StringUtils.defaultString(message).split("\\R");
        for (String line : lines) {
            String trim = line.trim();
            int idx = trim.indexOf('=');
            if (idx <= 0) continue;
            String key = trim.substring(0, idx).trim();
            String value = trim.substring(idx + 1).trim();
            if (isSupportedKey(key) && StringUtils.isNotBlank(value)) {
                map.put(key, value);
            }
        }
        return map;
    }

    private boolean isSupportedKey(String key) {
        return "zipPath".equals(key) || "projectPath".equals(key) || "gitUrl".equals(key)
                || "branch".equals(key) || "task".equals(key);
    }

    private String removeDirectiveLines(String message) {
        StringBuilder sb = new StringBuilder();
        String[] lines = StringUtils.defaultString(message).split("\\R");
        for (String line : lines) {
            String trim = line.trim();
            int idx = trim.indexOf('=');
            if (idx > 0 && isSupportedKey(trim.substring(0, idx).trim())) continue;
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) return value;
        }
        return "请分析当前代码项目并给出可执行改造方案";
    }
}
