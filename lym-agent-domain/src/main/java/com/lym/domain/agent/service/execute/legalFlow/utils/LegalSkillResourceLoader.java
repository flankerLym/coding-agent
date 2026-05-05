package com.lym.domain.agent.service.execute.legalFlow.utils;

import com.lym.domain.agent.service.execute.legalFlow.model.skills.LegalSkillResource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LegalSkillResourceLoader {

    private static final String SKILL_ROOT = "skills";

    private final Map<String, LegalSkillResource> cache = new ConcurrentHashMap<>();

    public LegalSkillResource load(String skillId) {
        try {
            String normalizedSkillId = normalizeSkillId(skillId);
            return cache.computeIfAbsent(normalizedSkillId, this::loadFromClasspath);
        } catch (Exception e) {
            log.warn("加载 Legal Skill 失败，skillId={}, reason={}", skillId, e.getMessage());
            return LegalSkillResource.empty(skillId);
        }
    }

    private LegalSkillResource loadFromClasspath(String skillId) {
        String basePath = SKILL_ROOT + "/" + skillId + "/";

        String skillMarkdown = readIfExists(basePath + "skill.md");
        String metadataYaml = readIfExists(basePath + "metadata.yaml");
        String referenceYaml = readIfExists(basePath + "reference.yaml");
        String scriptPython = readIfExists(basePath + "script.py");

        String systemPrompt = extractSystemPrompt(metadataYaml);

        LegalSkillResource resource = new LegalSkillResource(
                skillId,
                skillMarkdown,
                metadataYaml,
                referenceYaml,
                scriptPython,
                systemPrompt
        );

        if (resource.isAvailable()) {
            log.info("加载 Legal Skill 成功，skillId={}, hasSystemPrompt={}, basePath={}",
                    skillId, resource.hasSystemPrompt(), basePath);
        } else {
            log.warn("未找到 Legal Skill 资源，skillId={}, basePath={}", skillId, basePath);
        }

        return resource;
    }

    private String readIfExists(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);

            if (!resource.exists()) {
                return "";
            }

            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.warn("读取 Skill 资源失败，path={}, reason={}", classpathLocation, e.getMessage());
            return "";
        }
    }

    private String normalizeSkillId(String skillId) {
        if (skillId == null || skillId.trim().isEmpty()) {
            throw new IllegalArgumentException("skillId 不能为空");
        }

        String normalized = skillId.trim();

        if (!normalized.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("非法 skillId: " + skillId);
        }

        return normalized;
    }

    private String extractSystemPrompt(String metadataYaml) {
        if (metadataYaml == null || metadataYaml.trim().isEmpty()) {
            return "";
        }

        String prompt = extractYamlValue(metadataYaml, "system_prompt");

        if (prompt == null || prompt.trim().isEmpty()) {
            prompt = extractYamlValue(metadataYaml, "systemPrompt");
        }

        return prompt == null ? "" : prompt.trim();
    }

    private String extractYamlValue(String yaml, String key) {
        String[] lines = yaml.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            String trimmed = line.trim();
            String prefix = key + ":";

            if (!trimmed.startsWith(prefix)) {
                continue;
            }

            int baseIndent = countLeadingSpaces(line);
            String after = trimmed.substring(prefix.length()).trim();

            if (after.startsWith("|") || after.startsWith(">")) {
                StringBuilder block = new StringBuilder();

                for (int j = i + 1; j < lines.length; j++) {
                    String nextLine = lines[j];

                    if (nextLine.trim().isEmpty()) {
                        block.append('\n');
                        continue;
                    }

                    int indent = countLeadingSpaces(nextLine);

                    if (indent <= baseIndent) {
                        break;
                    }

                    int removeIndent = Math.min(nextLine.length(), baseIndent + 2);
                    block.append(nextLine.substring(removeIndent)).append('\n');
                }

                return block.toString().trim();
            }

            return stripQuotes(after);
        }

        return "";
    }

    private int countLeadingSpaces(String value) {
        int count = 0;

        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }

        return count;
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }

        String text = value.trim();

        if ((text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))) {
            return text.substring(1, text.length() - 1);
        }

        return text;
    }
}