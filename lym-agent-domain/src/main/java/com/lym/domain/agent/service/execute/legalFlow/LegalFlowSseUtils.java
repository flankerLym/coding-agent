package com.lym.domain.agent.service.execute.legalFlow;

import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.model.entity.AutoAgentExecuteResultEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * SSE 输出工具，复用 AutoAgentExecuteResultEntity。
 */
@Slf4j
public class LegalFlowSseUtils {

    private LegalFlowSseUtils() {
    }

    public static void sendAnalysis(ResponseBodyEmitter emitter, Integer step, String content, String sessionId) {
        send(emitter, AutoAgentExecuteResultEntity.createAnalysisResult(step, content, sessionId));
    }

    public static void sendExecution(ResponseBodyEmitter emitter, Integer step, String content, String sessionId) {
        send(emitter, AutoAgentExecuteResultEntity.createExecutionResult(step, content, sessionId));
    }

    public static void sendSupervision(ResponseBodyEmitter emitter, Integer step, String content, String sessionId) {
        send(emitter, AutoAgentExecuteResultEntity.createSupervisionResult(step, content, sessionId));
    }

    public static void sendSummary(ResponseBodyEmitter emitter, String content, String sessionId) {
        send(emitter, AutoAgentExecuteResultEntity.createSummaryResult(content, sessionId));
    }

    public static void sendError(ResponseBodyEmitter emitter, String content, String sessionId) {
        send(emitter, AutoAgentExecuteResultEntity.createErrorResult(content, sessionId));
    }

    private static void send(ResponseBodyEmitter emitter, AutoAgentExecuteResultEntity result) {
        if (emitter == null || result == null) {
            return;
        }
        try {
            emitter.send("data: " + JSON.toJSONString(result) + "\n\n");
        } catch (Exception e) {
            log.error("LegalFlow SSE 输出失败：{}", e.getMessage(), e);
        }
    }

}
