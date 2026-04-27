package com.lym.domain.agent.service.execute.harnesscoding.workspace;

import com.lym.domain.agent.service.execute.harnesscoding.config.HarnessCodeProperties;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeWorkspace;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
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

        Files.createDirectories(workspaceDir);
        Files.createDirectories(artifactDir);
        Files.createDirectories(logDir);

        if (StringUtils.isNotBlank(context.getGitUrl())) {
            cloneRepository(context.getGitUrl(), context.getBranch(), projectRoot, logDir);
        } else {
            Files.createDirectories(projectRoot);
            if (StringUtils.isNotBlank(context.getZipPath())) {
                unzipSafely(Paths.get(context.getZipPath()), projectRoot);
            } else if (StringUtils.isNotBlank(context.getProjectPath())) {
                copyDirectory(Paths.get(context.getProjectPath()), projectRoot);
            } else {
                Files.writeString(projectRoot.resolve("TASK.md"), context.getTask(), StandardCharsets.UTF_8);
            }
        }

        return HarnessCodeWorkspace.builder()
                .workspaceDir(workspaceDir.toString())
                .projectRoot(projectRoot.toString())
                .artifactDir(artifactDir.toString())
                .logDir(logDir.toString())
                .build();
    }

    /**
     * GitHub 克隆入口。
     *
     * 当前实现用本机 git 命令克隆 public repo。后续如果你要接 MCP，可以替换这里：
     * 1. 从 Neo4j 查询 git/filesystem/shell MCP tool 定义
     * 2. 放入 Advisor 或 ToolCallback
     * 3. 让 MCP 在 sandbox 中执行 git clone，而不是直接 ProcessBuilder
     */
    private void cloneRepository(String gitUrl, String branch, Path projectRoot, Path logDir) throws IOException {
        if (!Boolean.TRUE.equals(properties.getEnableGitClone())) {
            throw new IOException("HarnessCode git clone 未启用，请配置 spring.ai.agent.harness-code.enable-git-clone=true");
        }

        validateGitUrl(gitUrl);

        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("clone");
        if (Boolean.TRUE.equals(properties.getShallowClone())) {
            command.add("--depth");
            command.add("1");
        }
        if (StringUtils.isNotBlank(branch)) {
            command.add("--branch");
            command.add(branch.trim());
            command.add("--single-branch");
        }
        command.add(gitUrl.trim());
        command.add(projectRoot.toAbsolutePath().normalize().toString());

        Path stdout = logDir.resolve("git-clone.stdout.log");
        Path stderr = logDir.resolve("git-clone.stderr.log");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectOutput(stdout.toFile());
        processBuilder.redirectError(stderr.toFile());

        try {
            Process process = processBuilder.start();
            boolean finished = process.waitFor(properties.getGitCloneTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("git clone 超时，已终止。gitUrl=" + gitUrl);
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String err = Files.exists(stderr) ? Files.readString(stderr, StandardCharsets.UTF_8) : "";
                throw new IOException("git clone 失败，exitCode=" + exitCode + "，错误=" + truncate(err, 2000));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("git clone 被中断", e);
        }
    }

    private void validateGitUrl(String gitUrl) throws IOException {
        String value = StringUtils.defaultString(gitUrl).trim();
        if (StringUtils.isBlank(value)) {
            throw new IOException("gitUrl不能为空");
        }

        String host = extractHost(value);
        if (Boolean.TRUE.equals(properties.getGithubOnly())) {
            if (StringUtils.isBlank(host) || properties.getAllowedGitHosts().stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
                throw new IOException("当前仅允许克隆 GitHub 仓库，host=" + host + "，可通过 allowed-git-hosts 增加企业 GitHub 域名");
            }
        }

        String lower = value.toLowerCase(Locale.ROOT);
        boolean supported = lower.startsWith("https://") || lower.startsWith("git@") || lower.startsWith("ssh://git@");
        if (!supported) {
            throw new IOException("只支持 https://、git@ 或 ssh://git@ 形式的 Git 仓库地址");
        }
    }

    private String extractHost(String gitUrl) throws IOException {
        try {
            if (gitUrl.startsWith("git@")) {
                int at = gitUrl.indexOf('@');
                int colon = gitUrl.indexOf(':', at + 1);
                if (at >= 0 && colon > at) {
                    return gitUrl.substring(at + 1, colon).trim();
                }
            }
            URI uri = new URI(gitUrl);
            return uri.getHost();
        } catch (URISyntaxException e) {
            throw new IOException("非法 gitUrl：" + gitUrl, e);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
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
