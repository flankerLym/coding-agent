package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * MetaIntentRouterNode 只负责路由：
 * clarify / decompose / open_strategy / standard。
 */
@Slf4j
@Service
public class MetaIntentRouterNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ClarifyQuestionNode clarifyQuestionNode;
    @Resource
    private StandardIntentNode standardIntentNode;
    @Resource
    private LegalQueryDecomposeNode legalQueryDecomposeNode;
    @Resource
    private OpenLegalStrategyNode openLegalStrategyNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String route = context.getValue("meta_route");
        if (route == null || route.trim().isEmpty()) {
            route = "standard";
        }

        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                3,
                "MetaIntentRouterNode：根据 Meta Intent 路由到 = " + route,
                context.getSessionId());

        log.info("MetaIntentRouterNode route={}", route);

        return switch (route) {
            case "clarify" -> clarifyQuestionNode.apply(request, context);
            case "decompose" -> legalQueryDecomposeNode.apply(request, context);
            case "open_strategy" -> openLegalStrategyNode.apply(request, context);
            default -> standardIntentNode.apply(request, context);
        };
    }

}
