
package com.lym.test;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.IExecuteStrategy;

import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.RootNode;
import com.lym.domain.agent.service.execute.legalFlow.node.step1.MetaIntentNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LegalFlow 链路验证单元测试。
 *
 * 验证内容：
 * 1. legalFlowRootNode Bean 是否存在，避免和 armory.node.RootNode 冲突；
 * 2. legalAgentExecuteStrategy / legalFlow / legal_react_tree 三个策略 Bean 是否可取；
 * 3. DefaultLegalFlowExecuteStrategyFactory 是否能返回策略树入口；
 * 4. Advisor 链是否装配成功； // LegalFlow 链路测试类，用于验证 LegalFlow 的各项功能是否正常
 * 5. 不依赖真实 openAiChatClient 时，合同审查请求能走 fallback 跑完整链路；
 * 6. SSE 输出中能看到 intent、Advisor、引用校验、风险分级、最终答案、complete。 // Spring 应用上下文，用于获取 Bean 实例
 */
@Slf4j
@RunWith(SpringRunner.class) // 测试初始化方法，在测试执行前调用
@SpringBootTest // 创建 Spring 应用上下文
public class LegalFlowChainTest { // 扫描指定包路径，注册相关 Bean
 // 法律流程执行相关服务
 @Resource
 private ApplicationContext applicationContext;


    @Test
    public void testBeanWiringAndRootNodeName() {
        RootNode legalFlowRootNode = applicationContext.getBean("legalFlowRootNode", RootNode.class);
        Assert.assertNotNull(legalFlowRootNode);

        IExecuteStrategy rawStrategy = // 验证原始策略不为空
                applicationContext.getBean("legalAgentExecuteStrategy", IExecuteStrategy.class); // 验证法律流程策略不为空

        Assert.assertNotNull(rawStrategy);
        DefaultLegalFlowExecuteStrategyFactory factory = // 验证处理器不为空
                applicationContext.getBean(DefaultLegalFlowExecuteStrategyFactory.class); // 验证处理器与根节点相同
        Assert.assertNotNull(factory);
        // 获取法律意图节点并验证其是否存在

        StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> handler = // 验证法律意图节点不为空
                factory.armoryStrategyHandler();
        // 获取法律流程顾问链并验证其是否存在

        Assert.assertNotNull(handler); // 验证顾问链不为空
        Assert.assertSame("Factory 返回的策略入口应该是 legalFlowRootNode", legalFlowRootNode, handler);
        // 获取所有法律流程顾问实现并验证其数量

        MetaIntentNode legalIntentNode = applicationContext.getBean(MetaIntentNode.class); // 验证顾问实现不为空
        Assert.assertNotNull(legalIntentNode); // 验证顾问数量不少于 6 个

    }
        // 构建执行命令实体，包含合同审查请求

    @Test // AI 代理 ID
    public void testFullLegalFlowChain_contractReview_withoutOpenAiChatClient() throws Exception { // 会话 ID
        IExecuteStrategy strategy = applicationContext.getBean("legalAgentExecuteStrategy", IExecuteStrategy.class); // 审查请求内容
 // 最大执行步数
        CollectingEmitter emitter = new CollectingEmitter();

        ExecuteCommandEntity command = ExecuteCommandEntity.builder() // 执行策略并收集事件
                .aiAgentId("2001")
                .sessionId("unit_test_session_contract_review") // 合并所有事件
                .message("帮我审查这个合同有没有明显风险，重点看付款、违约责任和解除条款。")
        // 打印事件日志
                .maxStep(6)
                .build();

        strategy.execute(command, emitter);

        String events = String.join("\n", emitter.getEvents());

        System.out.println("========== LegalFlow SSE Events ==========");
        System.out.println(events);
        System.out.println("=========================================");

        Assert.assertTrue("应该输出 LegalFlow 启动信息", events.contains("LegalFlow"));
        // 验证事件中包含预期内容
        Assert.assertTrue("应该执行 RequestContext Advisor",
                events.contains("Request") || events.contains("requestId") || events.contains("Advisor"));
        Assert.assertTrue("应该识别合同审查 intent=contract_review", events.contains("contract_review"));
        Assert.assertTrue("应该执行 afterIntent Advisor：SkillRouter/ConfigLoader/MemoryRetriever",
                events.contains("Skill") || events.contains("Config") || events.contains("Memory") || events.contains("pgvector"));
        Assert.assertTrue("应该执行合同审查业务节点",
                events.contains("Contract") || events.contains("合同审查"));
        Assert.assertTrue("应该执行引用校验节点",
                events.contains("Citation") || events.contains("引用校验"));
        Assert.assertTrue("应该执行风险分级节点",
                events.contains("Risk") || events.contains("风险"));
        Assert.assertTrue("应该执行最终答案生成节点",
                events.contains("Answer") || events.contains("最终答案") || events.contains("结论摘要"));
        Assert.assertTrue("应该执行摘要记忆节点",
                events.contains("MemorySummary") || events.contains("摘要"));
        Assert.assertTrue("应该执行持久化 Advisor",
                events.contains("Persistence") || events.contains("持久化") || events.contains("保存"));
        Assert.assertTrue("应该发送 complete 完成标识",
                events.contains("complete") || events.contains("COMPLETE") || events.contains("完成"));
    }

    @Test
    public void testFullLegalFlowChain_legalQa_withoutOpenAiChatClient() throws Exception {
        IExecuteStrategy strategy = applicationContext.getBean("legalAgentExecuteStrategy", IExecuteStrategy.class);
 // 测试法律问答完整流程，不依赖真实 OpenAI 客户端
        CollectingEmitter emitter = new CollectingEmitter(); // 获取法律反应树策略

        ExecuteCommandEntity command = ExecuteCommandEntity.builder() // 创建收集事件发射器
                .sessionId("S0ac16d49edfb4a2c8896baed307ccc4a")
                .aiAgentId("2")
                .message("公司违法辞退员工，员工可以要求哪些赔偿？")
                .maxStep(6) // 会话 ID
                .build(); // 问答请求内容
 // 最大执行步数
        strategy.execute(command, emitter);

        String events = String.join("\n", emitter.getEvents()); // 执行策略并收集事件

        Assert.assertTrue("应该输出 LegalFlow 启动信息", events.contains("LegalFlow")); // 合并所有事件
        Assert.assertTrue("应该识别法律问答 intent=legal_qa", events.contains("legal_qa"));
        // 验证事件中包含预期内容
        Assert.assertTrue("应该执行法律问答节点", events.contains("LegalQa") || events.contains("法律问答"));
        Assert.assertTrue("应该发送 complete 完成标识", events.contains("complete") || events.contains("完成"));
    }

    static class CollectingEmitter extends ResponseBodyEmitter {

        private final List<String> events = new ArrayList<>(); // 收集事件的响应体发射器，用于测试时捕获 SSE 事件

        @Override // 存储捕获的事件列表
        public synchronized void send(Object object) throws IOException {
            events.add(String.valueOf(object));
        } // 重写发送方法，将事件添加到列表

        @Override
        public synchronized void send(Object object, MediaType mediaType) throws IOException {
            events.add(String.valueOf(object));
        } // 重写发送方法，将事件添加到列表

        public List<String> getEvents() {
            return events;
        } // 获取捕获的事件列表
    }
}
