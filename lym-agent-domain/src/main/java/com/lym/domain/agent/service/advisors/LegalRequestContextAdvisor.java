package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LegalRequestContextAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void before(ExecuteCommandEntity request,
                       DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.trim().isEmpty()) {
            sessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
        }

        context.setTenantId("default");
        context.setUserId("anonymous");
        context.setProjectId(request.getAiAgentId());
        context.setSessionId(sessionId);
        context.setRequestId("req_" + UUID.randomUUID().toString().replace("-", ""));
        context.setCurrentTask(request.getMessage());

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 1,
                "Advisor[RequestContext]：生成 requestId=" + context.getRequestId(),
                context.getSessionId());
    }

}
