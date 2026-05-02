package com.lym.domain.agent.service.execute.legalFlow.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.armory.node.factory.advisors.LegalFlowAdvisorChain;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RootNode 是策略树适配入口，不做业务能力。
 *
 * <p>非 LLM 能力全部由 Advisor 处理；
 * LLM 能力从 LegalIntentNode 开始。</p>
 */
@Slf4j
@Service("legalFlowRootNode")
public class RootNode implements StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> {

    @Resource
    private LegalFlowAdvisorChain legalFlowAdvisorChain;

    @Resource
    private LegalIntentNode legalIntentNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        if (request == null) {
            throw new IllegalArgumentException("ExecuteCommandEntity不能为空");
        }
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            throw new IllegalArgumentException("用户问题不能为空");
        }

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 1,
                "RootNode：执行前置 Advisor 链。", request.getSessionId());

        legalFlowAdvisorChain.before(request, context);

        return legalIntentNode.apply(request, context);
    }

}
