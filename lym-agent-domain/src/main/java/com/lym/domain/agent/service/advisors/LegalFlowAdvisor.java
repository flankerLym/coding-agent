package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;

/**
 * LegalFlow Advisor。
 *
 * <p>Advisor 只做非 LLM 能力：上下文、Redis、Neo4j、MySQL、pgvector、持久化等。</p>
 */
public interface LegalFlowAdvisor {

    int order();

    default void before(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
    }

    default void afterIntent(ExecuteCommandEntity request,
                             DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
    }

    default void afterAnswer(ExecuteCommandEntity request,
                             DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
    }

}
