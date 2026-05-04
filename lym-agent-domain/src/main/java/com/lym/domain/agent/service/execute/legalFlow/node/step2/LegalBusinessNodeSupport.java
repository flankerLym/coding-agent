package com.lym.domain.agent.service.execute.legalFlow.node.step2;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import com.lym.domain.agent.service.execute.legalFlow.node.step3.ResultMergeNode;
import jakarta.annotation.Resource;

import java.util.*;

public abstract class LegalBusinessNodeSupport extends AbstractLegalLlmNodeSupport {

    @Resource
    protected ResultMergeNode resultMergeNode;

    protected abstract String nodeName();

    protected abstract String draftType();


    protected abstract ClientIdEnums clientIdEnums();

    protected abstract String systemPrompt();

    protected abstract String fallbackJson();

    protected String buildUserPrompt(ExecuteCommandEntity request,
                                     DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String currentSubTask = context.getValue("current_sub_task");
        String taskMode = context.getValue("task_mode");

        StringBuilder prompt = new StringBuilder();
        prompt.append("用户原始问题：\n").append(request.getMessage());

        if (currentSubTask != null && !currentSubTask.trim().isEmpty()) {
            prompt.append("\n\n当前需要优先处理的子任务：\n").append(currentSubTask);
        }

        prompt.append("\n\n任务模式：\n").append(taskMode == null ? "single" : taskMode);
        prompt.append("\n\n最近上下文：\n").append(JSON.toJSONString(context.getRecentContext()));
        prompt.append("\n\nSkill路径：\n").append(JSON.toJSONString(context.getSkillPath()));
        prompt.append("\n\nMCP工具：\n").append(JSON.toJSONString(context.getMcpTools()));
        prompt.append("\n\n检索材料：\n").append(JSON.toJSONString(context.getMemoryHits()));
        prompt.append("\n\n请严格输出 JSON，不要输出额外解释。");
        return prompt.toString();
    }

    protected LegalDraftResult callAndParseDraft(ExecuteCommandEntity request,
                                                 DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String content = callLegalChatClient(
                clientIdEnums(),
                systemPrompt(),
                buildUserPrompt(request, context),
                fallbackJson()
        );

        try {
            JSONObject jsonObject = JSON.parseObject(content);
            return LegalDraftResult.builder()
                    .draftType(defaultString(jsonObject.getString("draft_type"), draftType()))
                    .draftAnswer(defaultString(jsonObject.getString("draft_answer"), fallbackAnswer()))
                    .keyFindings(toStringList(jsonObject, "key_findings"))
                    .riskPoints(toStringList(jsonObject, "risk_points"))
                    .missingInfo(toStringList(jsonObject, "missing_info"))
                    .build();
        } catch (Exception e) {
            return LegalDraftResult.builder()
                    .draftType(draftType())
                    .draftAnswer(fallbackAnswer())
                    .keyFindings(new ArrayList<>())
                    .riskPoints(new ArrayList<>())
                    .missingInfo(new ArrayList<>())
                    .build();
        }
    }

    protected String afterDraft(ExecuteCommandEntity request,
                                DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                                LegalDraftResult draftResult) throws Exception {
        context.setDraftResult(draftResult);
        appendAgentResult(context, draftResult);

        context.addTrace(nodeName() + "(openAiChatClient)：生成草稿：" + draftResult.getDraftAnswer());

        Boolean subTaskExecutionMode = context.getValue("subtask_execution_mode");
        if (Boolean.TRUE.equals(subTaskExecutionMode)) {
            return "LEGAL_SUB_TASK_DONE";
        }

        return router(request, context, resultMergeNode);
    }

    protected void appendAgentResult(DefaultLegalFlowExecuteStrategyFactory.DynamicContext context,
                                     LegalDraftResult draftResult) {
        List<Map<String, Object>> agentResults = context.getValue("agent_results");
        if (agentResults == null) {
            agentResults = new ArrayList<>();
            context.setValue("agent_results", agentResults);
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("node", nodeName());
        item.put("intent", context.getIntentCode());
        item.put("task_mode", context.getValue("task_mode"));
        item.put("sub_task", context.getValue("current_sub_task"));
        item.put("draft_result", draftResult);
        agentResults.add(item);
    }

    protected String fallbackAnswer() {
        try {
            JSONObject jsonObject = JSON.parseObject(fallbackJson());
            String answer = jsonObject.getString("draft_answer");
            return answer == null ? "" : answer;
        } catch (Exception e) {
            return "";
        }
    }

    protected List<String> toStringList(JSONObject jsonObject, String key) {
        if (jsonObject.getJSONArray(key) == null) {
            return new ArrayList<>();
        }
        return jsonObject.getJSONArray(key).toJavaList(String.class);
    }

    protected String defaultString(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
