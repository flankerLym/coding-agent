package com.lym.domain.agent.service.execute.legalFlow.node.step3;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step4.RiskGateNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
@Slf4j
@Service
public class CitationVerifyNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private RiskGateNode riskGateNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律引用校验 Agent。
                检查草稿答案中的法律判断是否被检索材料支持。
                不要新增法律结论。
                输出 JSON：
                {"citation_status":"pass|partial|fail","supported_claims":[],"unsupported_claims":[],"suggestion":""}
                """;

        String userPrompt = "草稿：\n" + JSON.toJSONString(context.getDraftResult())
                + "\n\n检索材料：\n" + JSON.toJSONString(context.getMemoryHits());

        String fallback = "{\"citation_status\":\"partial\",\"supported_claims\":[\"通用风险提示可由检索材料支持\"],\"unsupported_claims\":[\"具体结论仍需补充完整材料\"],\"suggestion\":\"建议补充法律依据或完整材料\"}";
        String content = callLegalChatClient(ClientIdEnums.CITATION_VERIFY, systemPrompt, userPrompt, fallback);
        log.info("引用校验Node 执行完成，clientId:{} beanName:{} result:{}",
                ClientIdEnums.LEGAL_INTENT.getClientId(),
                ClientIdEnums.LEGAL_INTENT.getBeanName(),
                content == null ? null : content.substring(0, Math.min(content.length(), 500)));
        try {
            JSONObject jsonObject = JSON.parseObject(content);
            context.setCitationStatus(jsonObject.getString("citation_status"));
            context.setSupportedClaims(jsonObject.getJSONArray("supported_claims") == null ? new ArrayList<>() : jsonObject.getJSONArray("supported_claims").toJavaList(String.class));
            context.setUnsupportedClaims(jsonObject.getJSONArray("unsupported_claims") == null ? new ArrayList<>() : jsonObject.getJSONArray("unsupported_claims").toJavaList(String.class));
            context.setCitationSuggestion(jsonObject.getString("suggestion"));
        } catch (Exception e) {
            context.setCitationStatus("partial");
            context.setSupportedClaims(new ArrayList<>());
            context.setUnsupportedClaims(new ArrayList<>());
            context.setCitationSuggestion("引用校验解析失败，采用保守处理。");
        }

        LegalFlowSseUtils.sendSupervision(context.getEmitter(), 6,
                "CitationVerifyNode(openAiChatClient)：citationStatus=" + context.getCitationStatus(),
                context.getSessionId());

        return router(request, context, get(request, context));
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return riskGateNode;
    }

}
