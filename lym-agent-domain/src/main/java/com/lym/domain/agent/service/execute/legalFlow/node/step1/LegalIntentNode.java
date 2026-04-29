package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.armory.node.factory.advisors.LegalFlowAdvisorChain;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Service
public class LegalIntentNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private LegalFlowAdvisorChain legalFlowAdvisorChain;

    @Resource
    private IntentRouterNode intentRouterNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        String systemPrompt = """
                你是法律助手的意图识别 Agent。
                只能从以下 intent 中选择：
                contract_review, legal_qa, case_search, compliance_check, document_drafting, general_chat。
                输出 JSON：
                {"intent":"","confidence":0.0,"reason":"","need_document":false,"need_retrieval":false}
                不要输出额外解释。
                """;

        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext())
                + "\n\n请判断法律任务类型。";

        String fallback = JSON.toJSONString(ruleIntent(request.getMessage()));
        String content = callOpenAiChatClient(applicationContext, systemPrompt, userPrompt, fallback);

        JSONObject jsonObject;
        try {
            jsonObject = JSON.parseObject(content);
        } catch (Exception e) {
            jsonObject = ruleIntent(request.getMessage());
        }

        context.setIntentCode(jsonObject.getString("intent"));
        context.setIntentConfidence(jsonObject.getDouble("confidence"));
        context.setIntentReason(jsonObject.getString("reason"));
        context.setNeedDocument(jsonObject.getBoolean("need_document"));
        context.setNeedRetrieval(jsonObject.getBoolean("need_retrieval"));

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 3,
                "LegalIntentNode(openAiChatClient)：intent=" + context.getIntentCode()
                        + " confidence=" + context.getIntentConfidence()
                        + " reason=" + context.getIntentReason(),
                context.getSessionId());

        // Intent 出来后，执行后置 Advisor：Neo4jSkillRouter / MySQLConfigLoader / PgVectorRetriever
        legalFlowAdvisorChain.afterIntent(request, context);

        return router(request, context, get(request, context));
    }

    private JSONObject ruleIntent(String message) {
        String text = message == null ? "" : message;
        JSONObject object = new JSONObject();
        if (containsAny(text, "合同", "协议", "条款", "违约", "付款", "解除")) {
            object.put("intent", "contract_review");
            object.put("confidence", 0.92);
            object.put("reason", "用户问题包含合同/条款/违约等关键词。");
            object.put("need_document", true);
            object.put("need_retrieval", true);
        } else if (containsAny(text, "案例", "判例", "类案", "裁判", "法院")) {
            object.put("intent", "case_search");
            object.put("confidence", 0.90);
            object.put("reason", "用户问题包含案例/判例/裁判等关键词。");
            object.put("need_document", false);
            object.put("need_retrieval", true);
        } else if (containsAny(text, "合规", "监管", "处罚", "资质", "数据安全", "隐私")) {
            object.put("intent", "compliance_check");
            object.put("confidence", 0.88);
            object.put("reason", "用户问题包含合规/监管/资质等关键词。");
            object.put("need_document", false);
            object.put("need_retrieval", true);
        } else if (containsAny(text, "起草", "草拟", "帮我写", "模板", "函", "声明")) {
            object.put("intent", "document_drafting");
            object.put("confidence", 0.86);
            object.put("reason", "用户问题包含起草/草拟/模板等关键词。");
            object.put("need_document", false);
            object.put("need_retrieval", true);
        } else if (containsAny(text, "法律", "劳动", "赔偿", "仲裁", "诉讼", "公司", "股权", "离职", "辞退")) {
            object.put("intent", "legal_qa");
            object.put("confidence", 0.84);
            object.put("reason", "用户问题属于一般法律咨询。");
            object.put("need_document", false);
            object.put("need_retrieval", true);
        } else {
            object.put("intent", "general_chat");
            object.put("confidence", 0.60);
            object.put("reason", "未识别到明确法律任务。");
            object.put("need_document", false);
            object.put("need_retrieval", false);
        }
        return object;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return intentRouterNode;
    }

}
