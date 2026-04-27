package com.lym.domain.agent.service.execute.harnesscoding.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.agent.harness-code")
public class HarnessCodeProperties {
    /** HarnessCode 每次执行创建的隔离工作目录 */
    private String workspaceRoot = "/tmp/lym-agent-harnesscode/workspaces";

    /** 扫描项目时最多读取多少个文件 */
    private Integer maxScanFiles = 300;

    /** 单文件最大读取字节数，避免把超大文件塞给模型 */
    private Integer maxFileBytes = 64 * 1024;

    /** 是否允许后续 verifier 执行 mvn/npm/python 等命令；默认关闭，避免运行用户仓库中的任意代码 */
    private Boolean enableCommandExecute = false;

    /** 是否允许 HarnessCode 执行 git clone；GitHub 分析场景需要打开 */
    private Boolean enableGitClone = true;

    /** git clone 超时时间 */
    private Integer gitCloneTimeoutSeconds = 180;

    /** 是否只允许克隆 GitHub 仓库；强烈建议保持 true */
    private Boolean githubOnly = true;

    /** 允许的 GitHub host，可加企业 GitHub 域名，例如 github.mycorp.com */
    private List<String> allowedGitHosts = new ArrayList<>(List.of("github.com", "www.github.com"));

    /** clone 时是否使用 --depth 1，提升速度并减少磁盘占用 */
    private Boolean shallowClone = true;

    /** verifier 命令超时时间 */
    private Integer commandTimeoutSeconds = 120;
}
