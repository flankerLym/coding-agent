package com.lym.domain.agent.service.advisors;

import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LegalPersistenceAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void afterAnswer(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        // 第一阶段：日志占位。后续替换为 MySQL / Redis / pgvector 写入。
        log.info("Advisor[Persistence] requestId:{} sessionId:{} shouldSaveMemory:{} context:{}",
                context.getRequestId(), context.getSessionId(), context.getShouldSaveMemory(), JSON.toJSONString(context));

        LegalFlowSseUtils.sendExecution(context.getEmitter(), 12,
                "Advisor[Persistence]：保存占位完成，后续接 MySQL / Redis / pgvector。",
                context.getSessionId());
    }

}
