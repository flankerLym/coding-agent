package com.lym.domain.agent.service.execute.legalFlow.node.step3;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.utils.MessageRecordServer;
import com.lym.domain.agent.service.execute.legalFlow.utils.ShortMemoryServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class AnswerGenerateNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private MessageRecordServer messageRecordServer;

    @Resource
    private ShortMemoryServer shortMemoryServer;

    @Resource
    private MemorySummaryNode memorySummaryNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律助手最终答案生成 Agent。
                整合草稿、引用校验和风险分级结果生成最终回答。
                要求：
                1. 先给结论摘要。
                2. 再分点说明分析依据、风险点和下一步建议。
                3. 如果依据不足，必须明确说明。
                4. 如果风险为 high，必须建议咨询执业律师。
                5. 结尾包含：本回答仅供参考，不构成正式法律意见。
                输出用户可读文本，不要输出 JSON。
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n草稿：\n" + JSON.toJSONString(context.getDraftResult())
                + "\n\n引用校验：\n" + JSON.toJSONString(context.getCitationStatus())
                + "\n\n风险分级：\n" + context.getRiskLevel() + " / " + context.getRiskReason()
                + "\n\n检索材料：\n" + JSON.toJSONString(context.getMemoryHits());

        String fallback = buildFallbackAnswer(context);
        String content = callLegalChatClient(ClientIdEnums.ANSWER_GENERATE, systemPrompt, userPrompt, fallback);
        context.setFinalAnswer(content);
        //保存对话记录
        messageRecordServer.recordMessage(request, context, content);

        shortMemoryServer.addShortMessage(request, context);


        log.info("回复生成Node 执行完成，clientId:{} beanName:{} result:{}",
                ClientIdEnums.LEGAL_INTENT.getClientId(),
                ClientIdEnums.LEGAL_INTENT.getBeanName(),
                content == null ? null : content.substring(0, Math.min(content.length(), 500)));
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 8,
                "AnswerGenerateNode(openAiChatClient)：最终答案生成完成。",
                context.getSessionId());

        return router(request, context, get(request, context));
    }

    private String buildFallbackAnswer(DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        StringBuilder answer = new StringBuilder();
        answer.append("结论摘要：").append(context.getDraftResult() == null ? "当前信息不足。" : context.getDraftResult().getDraftAnswer()).append("\n\n");
        answer.append("风险等级：").append(context.getRiskLevel()).append("\n");
        answer.append("风险原因：").append(context.getRiskReason()).append("\n\n");
        answer.append("引用校验：").append(context.getCitationStatus()).append("。");
        if (context.getCitationSuggestion() != null) {
            answer.append(context.getCitationSuggestion()).append("\n\n");
        }
        answer.append("建议下一步：请补充完整材料，尤其是合同全文、事实经过、时间地点和相关证据。\n\n");
        answer.append("免责声明：本回答仅供参考，不构成正式法律意见；重要事项请咨询执业律师。");
        return answer.toString();
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return memorySummaryNode;
    }

}
