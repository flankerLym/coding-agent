package com.lym.domain.agent.service.execute.legalFlow.config;

import com.lym.domain.agent.service.IExecuteStrategy;
import com.lym.domain.agent.service.execute.legalFlow.legalAgentExecuteStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 策略别名配置。
 *
 * <p>调度器会用 ai_agent.strategy 到 Map<String, IExecuteStrategy> 里找 Bean。
 * 这里提供多个策略名，方便数据库配置。</p>
 */
@Configuration
public class LegalFlowStrategyAliasConfig {

    @Bean("legalFlow")
    public IExecuteStrategy legalFlow(legalAgentExecuteStrategy delegate) {
        return delegate;
    }

    @Bean("legal_react_tree")
    public IExecuteStrategy legalReactTree(legalAgentExecuteStrategy delegate) {
        return delegate;
    }

}
