package com.lym.domain.agent.service.execute.harnesscoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessCodeVerifyResult {
    private boolean executed;
    private boolean success;
    private String command;
    private String output;

    public String toDisplayText() {
        if (!executed) {
            return "验证命令建议：" + command + "\n说明：当前未开启服务器命令执行，仅生成验证建议。";
        }
        return "验证命令：" + command + "\n执行结果：" + (success ? "通过" : "失败") + "\n" + output;
    }
}
