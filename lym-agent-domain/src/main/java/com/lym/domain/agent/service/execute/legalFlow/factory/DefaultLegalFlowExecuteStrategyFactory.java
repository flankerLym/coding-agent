package com.lym.domain.agent.service.execute.legalFlow.factory;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalMemoryHit;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalSkillConfig;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalToolConfig;
import com.lym.domain.agent.service.execute.legalFlow.node.RootNode;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.*;

/**
 * LegalFlow 策略工厂。
 *
 * <p>对齐 auto 的 Factory + StrategyHandler + DynamicContext 形式。</p>
 */
@Service
public class DefaultLegalFlowExecuteStrategyFactory {

    @Resource(name = "legalFlowRootNode")
    private RootNode rootNode;

    public StrategyHandler<ExecuteCommandEntity, DynamicContext, String> armoryStrategyHandler() {
        return rootNode;
    }

    @Data
    public static class DynamicContext {

        private ResponseBodyEmitter emitter;
        private Integer maxStep;
        private String currentTask;
        private StringBuilder executionHistory;

        private String tenantId;
        private String userId;
        private String projectId;
        private String sessionId;
        private String requestId;
        private List<String> recentContext = new ArrayList<>();

        private String intentCode;
        private Double intentConfidence;
        private String intentReason;
        private Boolean needRetrieval;
        private Boolean needDocument;

        private List<String> skillPath = new ArrayList<>();
        private List<LegalSkillConfig> skillConfigs = new ArrayList<>();
        private List<LegalToolConfig> mcpTools = new ArrayList<>();
        private List<LegalMemoryHit> memoryHits = new ArrayList<>();

        private List<String> reactTrace = new ArrayList<>();
        private LegalDraftResult draftResult;

        private String citationStatus;
        private List<String> supportedClaims = new ArrayList<>();
        private List<String> unsupportedClaims = new ArrayList<>();
        private String citationSuggestion;

        private String riskLevel;
        private String riskReason;
        private Boolean needHumanReview;

        private String finalAnswer;

        private Boolean shouldSaveMemory;
        private String memorySummary;

        private Map<String, Object> data = new HashMap<>();

        public void setValue(String key, Object value) {
            data.put(key, value);
        }

        @SuppressWarnings("unchecked")
        public <T> T getValue(String key) {
            return (T) data.get(key);
        }

        public void appendHistory(String content) {
            if (executionHistory == null) {
                executionHistory = new StringBuilder();
            }
            executionHistory.append(content).append("\n");
        }

        public void addTrace(String trace) {
            if (reactTrace == null) {
                reactTrace = new ArrayList<>();
            }
            reactTrace.add(trace);
            appendHistory(trace);
        }

    }

}
