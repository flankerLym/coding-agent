package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RootNode 仍然从这里进入，保持原有接线不变。
 *
 * 这个类现在承担“Meta Intent 识别入口”的职责：
 * 1. 判断是否需要澄清
 * 2. 判断是否属于多意图复杂任务
 * 3. 判断是否属于开放性问题
 * 4. 否则进入标准意图识别
 *
 * 这样可以在“不改 Advisor、不改 RootNode”的前提下，完成 step1 的工业级改造。
 */
@Slf4j
@Service
public class MetaIntentNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private MetaIntentRouterNode metaIntentRouterNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律助手的 Meta Intent Agent。
                请先判断用户问题属于哪一种执行模式，只输出 JSON：
                {
                  "question_type":"single|multi|open",
                  "need_clarification":false,
                  "need_decompose":false,
                  "route":"standard|clarify|decompose|open_strategy",
                  "confidence":0.0,
                  "title":"",
                  "reason":""
                }

                规则：
                1. 如果关键信息缺失、无法直接执行，route=clarify。
                2. 如果一个请求同时包含两个及以上可独立执行的任务，route=decompose。
                3. 如果问题明显是方案设计、开放式咨询、系统规划、多角度建议，route=open_strategy。
                4. 其他可直接执行的常规任务，route=standard。
                5. 为用户开启的这段对话起一个标题10个字以内，填入title
                6. 不要输出任何额外解释。
                """;

        String userPrompt = "用户问题：\n" + request.getMessage() +
                "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext()) +
                "\n\n请输出 Meta Intent JSON。";

        JSONObject fallback = ruleMetaIntent(request.getMessage());
        String content = callLegalChatClient(ClientIdEnums.LEGAL_INTENT,
                systemPrompt,
                userPrompt,
                fallback.toJSONString());

        JSONObject meta;
        try {
            meta = JSON.parseObject(content);
        } catch (Exception e) {
            meta = fallback;
        }
        String title = safeString(meta, "title", fallback.getString("title"));
        context.setValue("meta_title", title);
        String questionType = safeString(meta, "question_type", fallback.getString("question_type"));
        String route = safeString(meta, "route", fallback.getString("route"));
        Boolean needClarification = safeBoolean(meta, "need_clarification", fallback.getBoolean("need_clarification"));
        Boolean needDecompose = safeBoolean(meta, "need_decompose", fallback.getBoolean("need_decompose"));
        Double confidence = safeDouble(meta, "confidence", fallback.getDouble("confidence"));
        String reason = safeString(meta, "reason", fallback.getString("reason"));

        context.setValue("meta_question_type", questionType);
        context.setValue("meta_route", route);
        context.setValue("meta_need_clarification", needClarification);
        context.setValue("meta_need_decompose", needDecompose);
        context.setValue("meta_confidence", confidence);
        context.setValue("meta_reason", reason);

        context.addTrace("[MetaIntent] type=" + questionType +
                ", route=" + route +
                ", needClarification=" + needClarification +
                ", needDecompose=" + needDecompose +
                ", confidence=" + confidence +
                ", reason=" + reason);

        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                2,
                "LegalIntentNode(MetaIntent)：route=" + route + " type=" + questionType + " reason=" + reason,
                context.getSessionId());

        log.info("MetaIntent 执行完成，route:{} questionType:{} confidence:{} reason:{}",
                route, questionType, confidence, reason);

        return router(request, context, metaIntentRouterNode);
    }

    private JSONObject ruleMetaIntent(String message) {
        String text = message == null ? "" : message.trim();
        JSONObject obj = new JSONObject();

        if (text.isEmpty()) {
            obj.put("question_type", "single");
            obj.put("need_clarification", true);
            obj.put("need_decompose", false);
            obj.put("route", "clarify");
            obj.put("confidence", 0.99D);
            obj.put("reason", "用户问题为空或信息不足。");
            return obj;
        }

        boolean hasMultipleActions = containsAny(text,
                "并", "然后", "同时", "另外", "再", "顺便", "以及", "并且", "再帮我", "同时帮我");
        boolean isOpenQuestion = containsAny(text,
                "怎么设计", "如何设计", "方案", "思路", "建议", "整体", "规划", "你怎么看", "有什么办法", "如何搭建");
        boolean lacksKeyInfo = containsAny(text,
                "这个怎么办", "这个可以吗", "这个有风险吗")
                && !containsAny(text, "合同", "协议", "案例", "判例", "劳动", "起草", "草拟", "合规");

        if (lacksKeyInfo) {
            obj.put("question_type", "single");
            obj.put("need_clarification", true);
            obj.put("need_decompose", false);
            obj.put("route", "clarify");
            obj.put("confidence", 0.78D);
            obj.put("reason", "问题缺少必要对象或上下文信息。");
        } else if (isOpenQuestion) {
            obj.put("question_type", "open");
            obj.put("need_clarification", false);
            obj.put("need_decompose", false);
            obj.put("route", "open_strategy");
            obj.put("confidence", 0.84D);
            obj.put("reason", "用户问题偏向方案设计/开放咨询。");
        } else if (hasMultipleActions) {
            obj.put("question_type", "multi");
            obj.put("need_clarification", false);
            obj.put("need_decompose", true);
            obj.put("route", "decompose");
            obj.put("confidence", 0.83D);
            obj.put("reason", "用户问题包含多个动作或子任务。");
        } else {
            obj.put("question_type", "single");
            obj.put("need_clarification", false);
            obj.put("need_decompose", false);
            obj.put("route", "standard");
            obj.put("confidence", 0.86D);
            obj.put("reason", "用户问题可直接进入标准意图识别。");
        }
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

    private String safeString(JSONObject jsonObject, String key, String defaultValue) {
        String value = jsonObject.getString(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    private Boolean safeBoolean(JSONObject jsonObject, String key, Boolean defaultValue) {
        Boolean value = jsonObject.getBoolean(key);
        return value == null ? defaultValue : value;
    }

    private Double safeDouble(JSONObject jsonObject, String key, Double defaultValue) {
        Double value = jsonObject.getDouble(key);
        return value == null ? defaultValue : value;
    }
}
