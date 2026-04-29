package com.lym.domain.agent.service.armory.node.factory.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegalRedisContextAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void before(ExecuteCommandEntity request,
                       DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        // 第一阶段：占位。第二阶段替换为 RedisTemplate 查询最近 10 条上下文。
        List<String> recent = new ArrayList<>();
        recent.add("最近上下文占位：法律助手受控 ReAct 对话。");
        context.setRecentContext(recent);

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 2,
                "Advisor[RedisContext]：读取最近上下文数量=" + recent.size(),
                context.getSessionId());
    }

}
