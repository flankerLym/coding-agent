package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.utils.MessageRecordServer;
import com.lym.domain.agent.service.execute.legalFlow.utils.ShortMemoryServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 信息不足时，优先发起澄清，而不是误判后继续执行。
 */
@Slf4j
@Service
public class ClarifyQuestionNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private MessageRecordServer messageRecordServer;

    @Resource
    private ShortMemoryServer shortMemoryServer;
    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String systemPrompt = """
                你是法律助手的澄清问题 Agent。
                你的任务是：
                1. 指出当前信息不足以继续执行；
                2. 只提出 1~3 个最关键、最短的澄清问题；
                3. 使用中文，简洁专业；
                4. 不做法律结论，不展开分析。
                """;

        String userPrompt = "请基于以下用户问题，生成简洁澄清问题：\n" + request.getMessage();
        String fallback = "为了更准确地帮助你，请补充以下信息：\n"
                + "1. 你具体想处理的是哪一类法律事项？\n"
                + "2. 是否有相关合同、材料、案情或上下文可供参考？\n"
                + "3. 你希望我做的是审查、问答、检索、合规检查还是文书起草？";

        String content = callLegalChatClient(ClientIdEnums.CLARIFY_QUESTION, systemPrompt, userPrompt, fallback);
        context.setFinalAnswer(content);
        context.setValue("clarify_mode", true);
        context.addTrace("[Clarify] 触发澄清问题节点。answer=" + content);
        messageRecordServer.recordMessage(request, context, content);

        shortMemoryServer.addShortMessage(request, context);
        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                4,
                "ClarifyQuestionNode：信息不足，发起澄清。",
                context.getSessionId());

        log.info("ClarifyQuestionNode completed. result={}",
                content == null ? null : content.substring(0, Math.min(content.length(), 500)));
        return content;
    }
}
