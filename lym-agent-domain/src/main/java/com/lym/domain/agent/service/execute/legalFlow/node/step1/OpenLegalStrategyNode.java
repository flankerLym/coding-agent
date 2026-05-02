package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.armory.node.factory.advisors.LegalFlowAdvisorChain;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 开放性问题入口。
 *
 * 为了不改 step2，这里会给出一个 recommended_intent：
 * - 大多数开放性法律咨询 -> general_chat
 * - 如果明显偏向合规/合同/问答，也可以落到已有节点
 */
@Slf4j
@Service
public class OpenLegalStrategyNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private LegalFlowAdvisorChain legalFlowAdvisorChain;
    @Resource
    private IntentRouterNode intentRouterNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律助手的开放性问题分析 Agent。
                请为开放式问题推荐最合适的已有执行意图。
                只能从以下 intent 中选择：
                contract_review, legal_qa, case_search, compliance_check, document_drafting, general_chat。

                仅输出 JSON：
                {
                  "recommended_intent":"",
                  "answer_mode":"open_strategy",
                  "reason":"",
                  "need_document":false,
                  "need_retrieval":false
                }

                规则：
                1. 方案设计/系统规划/多角度建议，优先 general_chat；
                2. 如果开放问题明显偏向合同、合规、案例、文书，也可映射到对应 intent；
                3. 不要输出额外解释。
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext())
                + "\n\n请输出推荐 JSON。";

        JSONObject fallback = ruleOpenStrategy(request.getMessage());
        String content = callLegalChatClient(ClientIdEnums.OPEN_LEGAL_STRATEGY,
                systemPrompt,
                userPrompt,
                fallback.toJSONString());

        JSONObject jsonObject;
        try {
            jsonObject = JSON.parseObject(content);
        } catch (Exception e) {
            jsonObject = fallback;
        }

        String recommendedIntent = jsonObject.getString("recommended_intent");
        if (recommendedIntent == null || recommendedIntent.isBlank()) {
            recommendedIntent = fallback.getString("recommended_intent");
        }

        context.setIntentCode(recommendedIntent);
        context.setIntentConfidence(0.82D);
        context.setIntentReason(jsonObject.getString("reason"));
        context.setNeedDocument(jsonObject.getBoolean("need_document"));
        context.setNeedRetrieval(jsonObject.getBoolean("need_retrieval"));
        context.setValue("answer_mode", "open_strategy");
        context.setValue("open_strategy_reason", jsonObject.getString("reason"));

        context.addTrace("[OpenStrategy] recommendedIntent=" + recommendedIntent
                + ", reason=" + context.getIntentReason());

        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                4,
                "OpenLegalStrategyNode：recommendedIntent=" + recommendedIntent
                        + " reason=" + context.getIntentReason(),
                context.getSessionId());

        log.info("OpenLegalStrategyNode completed. recommendedIntent={} reason={}",
                recommendedIntent, context.getIntentReason());

        legalFlowAdvisorChain.afterIntent(request, context);
        return router(request, context, intentRouterNode);
    }

    private JSONObject ruleOpenStrategy(String message) {
        String text = message == null ? "" : message;
        JSONObject obj = new JSONObject();
        String intent = "general_chat";
        if (containsAny(text, "合规", "监管", "治理", "制度")) {
            intent = "compliance_check";
        } else if (containsAny(text, "合同", "协议", "条款")) {
            intent = "contract_review";
        } else if (containsAny(text, "案例", "判例", "类案")) {
            intent = "case_search";
        } else if (containsAny(text, "起草", "草拟", "模板")) {
            intent = "document_drafting";
        } else if (containsAny(text, "劳动", "赔偿", "仲裁", "诉讼", "法律")) {
            intent = "legal_qa";
        }

        obj.put("recommended_intent", intent);
        obj.put("answer_mode", "open_strategy");
        obj.put("reason", "开放性问题已映射到现有意图链路，优先复用既有业务节点。\n");
        obj.put("need_document", false);
        obj.put("need_retrieval", true);
        return obj;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
