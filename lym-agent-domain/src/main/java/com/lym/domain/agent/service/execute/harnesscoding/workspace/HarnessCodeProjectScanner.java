package com.lym.domain.agent.service.execute.harnesscoding.workspace;

import com.lym.domain.agent.service.execute.harnesscoding.config.HarnessCodeProperties;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeProjectSnapshot;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Component
public class HarnessCodeProjectScanner {

    @Resource
    private HarnessCodeProperties properties;

    private static final Set<String> IGNORE_DIRS = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "dist", "node_modules", ".venv", "venv", "__pycache__"
    );

    public HarnessCodeProjectSnapshot scan(String projectRoot) throws IOException {
        Path root = Paths.get(projectRoot);
        List<String> files = new ArrayList<>();
        List<String> importantFiles = new ArrayList<>();
        Set<String> techStacks = new LinkedHashSet<>();

        if (!Files.exists(root)) {
            return HarnessCodeProjectSnapshot.builder().projectRoot(projectRoot).build();
        }

        try (Stream<Path> stream = Files.walk(root, 8)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(root, path))
                    .limit(properties.getMaxScanFiles())
                    .forEach(path -> {
                        String relative = root.relativize(path).toString().replace('\\', '/');
                        files.add(relative);
                        if (isImportant(relative)) importantFiles.add(relative);
                        detectTechStack(relative, techStacks);
                    });
        }

        return HarnessCodeProjectSnapshot.builder()
                .projectRoot(projectRoot)
                .files(files)
                .importantFiles(importantFiles)
                .techStacks(new ArrayList<>(techStacks))
                .build();
    }

    private boolean isIgnored(Path root, Path path) {
        Path relative = root.relativize(path);
        for (Path item : relative) {
            if (IGNORE_DIRS.contains(item.toString())) return true;
        }
        return false;
    }

    private boolean isImportant(String relative) {
        String lower = relative.toLowerCase(Locale.ROOT);
        return lower.endsWith("pom.xml")
                || lower.endsWith("build.gradle")
                || lower.endsWith("package.json")
                || lower.endsWith("requirements.txt")
                || lower.endsWith("readme.md")
                || lower.contains("application.yml")
                || lower.contains("application-dev.yml")
                || lower.endsWith("docker-compose.yml")
                || lower.endsWith("dockerfile");
    }

    private void detectTechStack(String relative, Set<String> techStacks) {
        String lower = relative.toLowerCase(Locale.ROOT);
        if (lower.endsWith("pom.xml")) techStacks.add("Java/Maven");
        if (lower.endsWith("build.gradle") || lower.endsWith("build.gradle.kts")) techStacks.add("Java/Gradle");
        if (lower.endsWith("package.json")) techStacks.add("Node/React/Vue");
        if (lower.endsWith("requirements.txt") || lower.endsWith("pyproject.toml")) techStacks.add("Python");
        if (lower.contains("spring") || lower.contains("application.yml")) techStacks.add("Spring Boot");
        if (lower.endsWith("docker-compose.yml") || lower.endsWith("dockerfile")) techStacks.add("Docker");
    }
}
