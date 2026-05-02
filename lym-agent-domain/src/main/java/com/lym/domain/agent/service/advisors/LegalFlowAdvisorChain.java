package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class LegalFlowAdvisorChain {

    @Resource
    private List<LegalFlowAdvisor> advisors;

    public void before(ExecuteCommandEntity request,
                       DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        advisors.stream()
                .sorted(Comparator.comparingInt(LegalFlowAdvisor::order))
                .forEach(advisor -> callBefore(advisor, request, context));
    }

    public void afterIntent(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        advisors.stream()
                .sorted(Comparator.comparingInt(LegalFlowAdvisor::order))
                .forEach(advisor -> callAfterIntent(advisor, request, context));
    }

    public void afterAnswer(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        advisors.stream()
                .sorted(Comparator.comparingInt(LegalFlowAdvisor::order))
                .forEach(advisor -> callAfterAnswer(advisor, request, context));
    }

    private void callBefore(LegalFlowAdvisor advisor, ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        try {
            advisor.before(request, context);
        } catch (Exception e) {
            throw new RuntimeException("LegalFlow before advisor 执行失败：" + advisor.getClass().getSimpleName(), e);
        }
    }

    private void callAfterIntent(LegalFlowAdvisor advisor, ExecuteCommandEntity request,
                                 DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        try {
            advisor.afterIntent(request, context);
        } catch (Exception e) {
            throw new RuntimeException("LegalFlow afterIntent advisor 执行失败：" + advisor.getClass().getSimpleName(), e);
        }
    }

    private void callAfterAnswer(LegalFlowAdvisor advisor, ExecuteCommandEntity request,
                                 DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        try {
            advisor.afterAnswer(request, context);
        } catch (Exception e) {
            throw new RuntimeException("LegalFlow afterAnswer advisor 执行失败：" + advisor.getClass().getSimpleName(), e);
        }
    }

}
