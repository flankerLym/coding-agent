package com.lym.domain.agent.service.execute.legalFlow.node.step1;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 复杂多意图任务拆解。
 *
 * 当前版本遵循“只改 step1”的边界：
 * - 在这里先拆解出子任务，写入 DynamicContext.data；
 * - 同时给出一个 primary_intent，供既有 step2 执行链继续运行；
 * - 后续若你扩展 step2 / merge，可直接消费 sub_tasks。
 */
@Slf4j
@Service
public class LegalQueryDecomposeNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private LegalFlowAdvisorChain legalFlowAdvisorChain;
    @Resource
    private IntentRouterNode intentRouterNode;

/**
 * 重写 apply 方法，用于处理法律助手任务的复杂拆解
 * @param request 执行命令实体，包含用户请求信息
 * @param context 动态上下文，包含会话和执行环境信息
 * @return 处理结果字符串
 * @throws Exception 可能抛出的异常
 */
    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
    // 定义系统提示词，用于指导AI如何拆解用户问题
        String systemPrompt = """
                你是法律助手的复杂任务拆解 Agent。
                请将用户问题拆解为可执行子任务，并推断一个主意图。
                只能从以下 intent 中选择 primary_intent：
                contract_review, legal_qa, case_search, compliance_check, document_drafting, general_chat。

                仅输出 JSON：
                {
                  "primary_intent":"",
                  "sub_tasks":[""],
                  "reason":"",
                  "need_document":false,
                  "need_retrieval":false
                }

                要求：
                1. sub_tasks 使用中文短句；
                2. primary_intent 选择最核心、最先执行的那个；
                3. 不要输出额外解释。
                """;

    // 构建用户提示词，包含用户问题和最近上下文
        String userPrompt = "用户问题：\n" + request.getMessage()
                + "\n\n最近上下文：\n" + JSON.toJSONString(context.getRecentContext())
                + "\n\n请输出拆解 JSON。";

    // 获取回退结果，用于当AI处理失败时的备选方案
        JSONObject fallback = ruleDecompose(request.getMessage());
    // 调用法律聊天客户端获取处理结果
        String content = callLegalChatClient(ClientIdEnums.LEGAL_QUERY_DECOMPOSE,
                systemPrompt,
                userPrompt,
                fallback.toJSONString());

    // 解析AI返回的JSON，解析失败则使用回退结果
        JSONObject jsonObject;
        try {
            jsonObject = JSON.parseObject(content);
        } catch (Exception e) {
            jsonObject = fallback;
        }

    // 获取主意图，如果为空则使用回退结果
        String primaryIntent = jsonObject.getString("primary_intent");
        if (primaryIntent == null || primaryIntent.isBlank()) {
            primaryIntent = fallback.getString("primary_intent");
        }

    // 获取子任务列表，如果为空则使用回退结果
        List<String> subTasks = toStringList(jsonObject.getJSONArray("sub_tasks"));
        if (subTasks.isEmpty()) {
            subTasks = toStringList(fallback.getJSONArray("sub_tasks"));
        }

    // 设置上下文信息，包括意图代码、置信度、原因等
        context.setIntentCode(primaryIntent);
        context.setIntentConfidence(0.80D);
        context.setIntentReason(jsonObject.getString("reason"));
        context.setNeedDocument(jsonObject.getBoolean("need_document"));
        context.setNeedRetrieval(jsonObject.getBoolean("need_retrieval"));
        context.setValue("sub_tasks", subTasks);
        context.setValue("task_mode", "decompose");

    // 添加追踪信息
        context.addTrace("[Decompose] primaryIntent=" + primaryIntent + ", subTasks=" + subTasks);

    // 发送分析信息到SSE流
        LegalFlowSseUtils.sendAnalysis(
                context.getEmitter(),
                4,
                "LegalQueryDecomposeNode：主意图=" + primaryIntent + " subTasks=" + subTasks,
                context.getSessionId());

    // 记录日志
        log.info("LegalQueryDecomposeNode completed. primaryIntent={} subTasks={}", primaryIntent, subTasks);

        // 这里沿用原有 afterIntent 逻辑，不改 Advisor。
        legalFlowAdvisorChain.afterIntent(request, context);
        return router(request, context, intentRouterNode);
    }

    private JSONObject ruleDecompose(String message) {
        String text = message == null ? "" : message;
        JSONObject obj = new JSONObject();
        obj.put("primary_intent", inferPrimaryIntent(text));

        JSONArray subTasks = new JSONArray();
        String[] parts = text.split("[，,。；;]|然后|并且|同时|另外|再帮我|顺便");
        for (String part : parts) {
            String p = part == null ? "" : part.trim();
            if (!p.isEmpty()) {
                subTasks.add(p);
            }
        }
        if (subTasks.isEmpty()) {
            subTasks.add(text);
        }
        obj.put("sub_tasks", subTasks);
        obj.put("reason", "检测到用户问题含多个动作，已按自然语义拆解。\n");
        obj.put("need_document", text.contains("合同") || text.contains("协议") || text.contains("材料"));
        obj.put("need_retrieval", true);
        return obj;
    }

    private String inferPrimaryIntent(String text) {
        if (containsAny(text, "合同", "协议", "条款", "违约")) {
            return "contract_review";
        }
        if (containsAny(text, "起草", "草拟", "帮我写", "模板", "函")) {
            return "document_drafting";
        }
        if (containsAny(text, "案例", "判例", "裁判", "法院")) {
            return "case_search";
        }
        if (containsAny(text, "合规", "监管", "处罚", "隐私", "资质")) {
            return "compliance_check";
        }
        if (containsAny(text, "法律", "劳动", "诉讼", "仲裁", "赔偿")) {
            return "legal_qa";
        }
        return "general_chat";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private List<String> toStringList(JSONArray jsonArray) {
        List<String> list = new ArrayList<>();
        if (jsonArray == null) {
            return list;
        }
        for (Object item : jsonArray) {
            if (item != null) {
                String val = String.valueOf(item).trim();
                if (!val.isEmpty()) {
                    list.add(val);
                }
            }
        }
        return list;
    }
}
