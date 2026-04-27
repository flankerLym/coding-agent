package com.lym.domain.agent.service.execute.harnesscoding.workspace;

import com.lym.domain.agent.service.execute.harnesscoding.config.HarnessCodeProperties;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeWorkspace;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class HarnessCodeWorkspaceManager {

    @Resource
    private HarnessCodeProperties properties;

    public HarnessCodeWorkspace prepareWorkspace(HarnessCodeExecutionContext context) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path workspaceDir = Paths.get(properties.getWorkspaceRoot(), context.getSessionId() + "-" + timestamp);
        Path projectRoot = workspaceDir.resolve("project");
        Path artifactDir = workspaceDir.resolve("artifacts");
        Path logDir = workspaceDir.resolve("logs");

        Files.createDirectories(projectRoot);
        Files.createDirectories(artifactDir);
        Files.createDirectories(logDir);

        if (StringUtils.isNotBlank(context.getZipPath())) {
            unzipSafely(Paths.get(context.getZipPath()), projectRoot);
        } else if (StringUtils.isNotBlank(context.getProjectPath())) {
            copyDirectory(Paths.get(context.getProjectPath()), projectRoot);
        } else if (StringUtils.isNotBlank(context.getGitUrl())) {
            // 这里先不直接 git clone。
            // 后续建议通过 MCP git 工具 / sandbox 执行：git clone -b branch gitUrl projectRoot
            Files.writeString(projectRoot.resolve("GIT_CLONE_TODO.txt"),
                    "gitUrl=" + context.getGitUrl() + "\nbranch=" + StringUtils.defaultString(context.getBranch(), "master") + "\n");
        } else {
            Files.writeString(projectRoot.resolve("TASK.md"), context.getTask());
        }

        return HarnessCodeWorkspace.builder()
                .workspaceDir(workspaceDir.toString())
                .projectRoot(projectRoot.toString())
                .artifactDir(artifactDir.toString())
                .logDir(logDir.toString())
                .build();
    }

    private void unzipSafely(Path zipPath, Path targetDir) throws IOException {
        Path normalizedTarget = targetDir.toAbsolutePath().normalize();
        try (InputStream inputStream = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(inputStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = normalizedTarget.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(normalizedTarget)) {
                    throw new IOException("非法 zip entry，疑似 Zip Slip：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        try (var stream = Files.walk(source)) {
            stream.sorted(Comparator.naturalOrder()).forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path dest = target.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
