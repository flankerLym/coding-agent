package com.lym.domain.agent.service.execute.harnesscoding.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessCodeEvent {
    private String stage;
    private String subType;
    private String content;
    private LocalDateTime timestamp;

    public static HarnessCodeEvent of(String stage, String subType, String content) {
        return HarnessCodeEvent.builder().stage(stage).subType(subType).content(content).timestamp(LocalDateTime.now()).build();
    }

    public static HarnessCodeEvent analysis(String content) { return of("analysis", "task", content); }
    public static HarnessCodeEvent context(String content) { return of("analysis", "context", content); }
    public static HarnessCodeEvent skill(String content) { return of("analysis", "skill", content); }
    public static HarnessCodeEvent plan(String content) { return of("analysis", "plan", content); }
    public static HarnessCodeEvent execution(String content) { return of("execution", "tool", content); }
    public static HarnessCodeEvent verify(String content) { return of("supervision", "verify", content); }
    public static HarnessCodeEvent summary(String content) { return of("summary", "final", content); }
    public static HarnessCodeEvent error(String content) { return of("error", "exception", content); }
    public static HarnessCodeEvent done(String content) { return of("summary", "done", content); }
}
