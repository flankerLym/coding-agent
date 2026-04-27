package com.lym.domain.agent.service.execute.harnesscoding.verifier;

import com.lym.domain.agent.service.execute.harnesscoding.config.HarnessCodeProperties;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeExecutionContext;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeProjectSnapshot;
import com.lym.domain.agent.service.execute.harnesscoding.model.HarnessCodeVerifyResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class HarnessCodeVerifier {

    @Resource
    private HarnessCodeProperties properties;

    public HarnessCodeVerifyResult verify(HarnessCodeExecutionContext context) {
        String command = inferVerifyCommand(context.getSnapshot());

        if (!Boolean.TRUE.equals(properties.getEnableCommandExecute())) {
            return HarnessCodeVerifyResult.builder()
                    .executed(false)
                    .success(false)
                    .command(command)
                    .output("enable-command-execute=false，未执行不可信项目命令。")
                    .build();
        }

        try {
            Process process = new ProcessBuilder(shellCommand(command))
                    .directory(Paths.get(context.getWorkspace().getProjectRoot()).toFile())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(properties.getCommandTimeoutSeconds(), TimeUnit.SECONDS);
            String output = new BufferedReader(new InputStreamReader(process.getInputStream()))
                    .lines().collect(Collectors.joining("\n"));

            if (!finished) {
                process.destroyForcibly();
                return HarnessCodeVerifyResult.builder()
                        .executed(true)
                        .success(false)
                        .command(command)
                        .output("命令超时：" + output)
                        .build();
            }

            return HarnessCodeVerifyResult.builder()
                    .executed(true)
                    .success(process.exitValue() == 0)
                    .command(command)
                    .output(output)
                    .build();
        } catch (Exception e) {
            return HarnessCodeVerifyResult.builder()
                    .executed(true)
                    .success(false)
                    .command(command)
                    .output("验证执行异常：" + e.getMessage())
                    .build();
        }
    }

    private String inferVerifyCommand(HarnessCodeProjectSnapshot snapshot) {
        if (snapshot == null || snapshot.getFiles() == null) return "请手动补充验证命令";
        if (snapshot.getFiles().contains("pom.xml")) return "mvn test";
        if (snapshot.getFiles().contains("package.json")) return "npm test";
        if (snapshot.getFiles().contains("requirements.txt") || snapshot.getFiles().contains("pyproject.toml")) return "pytest";
        return "请根据项目类型补充验证命令";
    }

    private String[] shellCommand(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return new String[]{"cmd", "/c", command};
        return new String[]{"bash", "-lc", command};
    }
}
