package com.lym.trigger.http.admin;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.IAgentDispatchService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.annotation.Resource;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * HarnessCode GitHub 克隆入口。
 *
 * 用法：用户在你部署的网站输入 GitHub 仓库地址，后端把 gitUrl/branch/task 写入 ExecuteCommandEntity.message，
 * 然后复用现有 dispatch。只要 aiAgentId 对应的 strategy=harnesscode，就会进入 HarnessCodeExecuteStrategy。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/harness-code")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class HarnessCodeController {

    @Resource
    private IAgentDispatchService agentDispatchService;

    /**
     * GitHub 仓库克隆并分析。
     *
     * 请求示例：
     * {
     *   "aiAgentId": "6",
     *   "gitUrl": "https://github.com/flankerLym/coding-agent.git",
     *   "branch": "master",
     *   "message": "请分析这个项目并给出改造方案",
     *   "sessionId": "可选",
     *   "maxStep": 5
     * }
     */
    @PostMapping("github")
    public ResponseBodyEmitter cloneGithubAndAnalyze(@RequestBody HarnessCodeGithubRequest request,
                                                     HttpServletResponse response) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(Long.MAX_VALUE);
        try {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Connection", "keep-alive");

            if (request == null) {
                emitter.send("请求体不能为空");
                emitter.complete();
                return emitter;
            }
            if (StringUtils.isBlank(request.getAiAgentId())) {
                emitter.send("aiAgentId不能为空");
                emitter.complete();
                return emitter;
            }
            if (StringUtils.isBlank(request.getGitUrl())) {
                emitter.send("gitUrl不能为空，请传入 GitHub 仓库地址");
                emitter.complete();
                return emitter;
            }

            String safeSessionId = StringUtils.defaultIfBlank(request.getSessionId(), UUID.randomUUID().toString().replace("-", ""));
            Integer maxStep = request.getMaxStep() == null || request.getMaxStep() <= 0 ? 5 : request.getMaxStep();
            String task = StringUtils.defaultIfBlank(request.getMessage(), "请分析这个 GitHub 代码项目，给出可执行的修改方案和验证建议");

            String harnessMessage = "gitUrl=" + request.getGitUrl().trim() + "\n"
                    + "branch=" + StringUtils.defaultIfBlank(request.getBranch(), "") + "\n"
                    + "task=" + task;

            ExecuteCommandEntity command = ExecuteCommandEntity.builder()
                    .aiAgentId(request.getAiAgentId())
                    .sessionId(safeSessionId)
                    .message(harnessMessage)
                    .maxStep(maxStep)
                    .build();

            log.info("HarnessCode github request, aiAgentId={}, sessionId={}, gitUrl={}, branch={}",
                    request.getAiAgentId(), safeSessionId, request.getGitUrl(), request.getBranch());

            agentDispatchService.dispatch(command, emitter);
            return emitter;
        } catch (Exception e) {
            log.error("HarnessCode GitHub clone dispatch failed", e);
            try {
                emitter.send("HarnessCode GitHub执行异常：" + e.getMessage());
                emitter.complete();
            } catch (Exception ignored) {
            }
            return emitter;
        }
    }

    /**
     * 兼容原 auto_agent 通道：前端也可以直接调 /api/v1/agent/auto_agent，message 中传 gitUrl=xxx。
     * 这个专用入口只是为了让 React 页面更清晰，不需要用户手写 directive。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HarnessCodeGithubRequest implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String aiAgentId;
        private String gitUrl;
        private String branch;
        private String message;
        private String sessionId;
        private Integer maxStep;
    }
}
