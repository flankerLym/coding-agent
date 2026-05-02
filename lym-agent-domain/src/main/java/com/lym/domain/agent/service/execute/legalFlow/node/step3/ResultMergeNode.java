package com.lym.domain.agent.service.execute.legalFlow.node.step3;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.AbstractLegalLlmNodeSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ResultMergeNode extends AbstractLegalLlmNodeSupport {

    @Resource
    private CitationVerifyNode citationVerifyNode;

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        List<Map<String, Object>> agentResults = context.getValue("agent_results");

        if (agentResults == null || agentResults.isEmpty()) {
            LegalDraftResult fallbackDraft = context.getDraftResult();
            if (fallbackDraft == null) {
                fallbackDraft = LegalDraftResult.builder()
                        .draftType(defaultString(context.getIntentCode(), "general_chat"))
                        .draftAnswer("当前信息不足，未生成有效业务草稿。")
                        .keyFindings(new ArrayList<>())
                        .riskPoints(new ArrayList<>())
                        .missingInfo(List.of("请补充更完整的问题、材料或事实背景。"))
                        .build();
                context.setDraftResult(fallbackDraft);
            }

            LegalFlowSseUtils.sendExecution(context.getEmitter(), 6,
                    "ResultMergeNode：单任务或无子任务结果，跳过复杂聚合。",
                    context.getSessionId());
            return router(request, context, citationVerifyNode);
        }

        if (agentResults.size() == 1) {
            LegalDraftResult one = extractDraft(agentResults.get(0));
            if (one != null) {
                context.setDraftResult(one);
            }
            LegalFlowSseUtils.sendExecution(context.getEmitter(), 6,
                    "ResultMergeNode：仅 1 个业务结果，完成规范化。",
                    context.getSessionId());
            return router(request, context, citationVerifyNode);
        }

        String systemPrompt = """
                你是法律助手的子任务结果聚合 Agent。
                你的任务是把多个业务 Agent 的草稿聚合成一个统一、无重复、结构清晰的草稿。
                要求：不新增没有来源的事实、法条、案例或结论；保留每个子任务的关键发现、风险点、缺失信息；合并重复内容。
                输出 JSON：{"draft_type":"merged_result","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;

        String userPrompt = "用户原始问题：\n" + request.getMessage()
                + "\n\n子任务列表：\n" + JSON.toJSONString(context.getValue("sub_tasks"))
                + "\n\n多个业务 Agent 结果：\n" + JSON.toJSONString(agentResults)
                + "\n\n请聚合成一个统一 LegalDraftResult JSON。";

        String fallback = buildFallbackMergeJson(agentResults);
        String content = callLegalChatClient(ClientIdEnums.RESULT_MERGE, systemPrompt, userPrompt, fallback);

        LegalDraftResult merged;
        try {
            JSONObject jsonObject = JSON.parseObject(content);
            merged = LegalDraftResult.builder()
                    .draftType(defaultString(jsonObject.getString("draft_type"), "merged_result"))
                    .draftAnswer(defaultString(jsonObject.getString("draft_answer"), fallbackAnswer(agentResults)))
                    .keyFindings(toStringList(jsonObject, "key_findings"))
                    .riskPoints(toStringList(jsonObject, "risk_points"))
                    .missingInfo(toStringList(jsonObject, "missing_info"))
                    .build();
        } catch (Exception e) {
            merged = buildFallbackMerge(agentResults);
        }

        context.setDraftResult(merged);
        context.setValue("merged_result", merged);
        context.addTrace("[ResultMerge] merged " + agentResults.size() + " agent results.");
        LegalFlowSseUtils.sendExecution(context.getEmitter(), 6,
                "ResultMergeNode(openAiChatClient)：子任务结果聚合完成，数量=" + agentResults.size(),
                context.getSessionId());

        log.info("ResultMergeNode completed, count={}, answer={}", agentResults.size(), merged.getDraftAnswer());
        return router(request, context, citationVerifyNode);
    }

    private LegalDraftResult extractDraft(Map<String, Object> item) {
        if (item == null) {
            return null;
        }
        Object draft = item.get("draft_result");
        if (draft instanceof LegalDraftResult legalDraftResult) {
            return legalDraftResult;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(draft), LegalDraftResult.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildFallbackMergeJson(List<Map<String, Object>> agentResults) {
        return JSON.toJSONString(buildFallbackMerge(agentResults));
    }

    private LegalDraftResult buildFallbackMerge(List<Map<String, Object>> agentResults) {
        StringBuilder answer = new StringBuilder();
        LinkedHashSet<String> keyFindings = new LinkedHashSet<>();
        LinkedHashSet<String> riskPoints = new LinkedHashSet<>();
        LinkedHashSet<String> missingInfo = new LinkedHashSet<>();

        int index = 0;
        for (Map<String, Object> item : agentResults) {
            index++;
            LegalDraftResult draft = extractDraft(item);
            if (draft == null) {
                continue;
            }

            Object subTask = item.get("sub_task");
            answer.append("子任务 ").append(index);
            if (subTask != null && !String.valueOf(subTask).trim().isEmpty()) {
                answer.append("（").append(subTask).append("）");
            }
            answer.append("：").append(defaultString(draft.getDraftAnswer(), "未生成有效草稿")).append("\n\n");

            if (draft.getKeyFindings() != null) {
                keyFindings.addAll(draft.getKeyFindings());
            }
            if (draft.getRiskPoints() != null) {
                riskPoints.addAll(draft.getRiskPoints());
            }
            if (draft.getMissingInfo() != null) {
                missingInfo.addAll(draft.getMissingInfo());
            }
        }

        return LegalDraftResult.builder()
                .draftType("merged_result")
                .draftAnswer(defaultString(answer.toString().trim(), "已完成多个子任务分析，但结果内容为空。"))
                .keyFindings(new ArrayList<>(keyFindings))
                .riskPoints(new ArrayList<>(riskPoints))
                .missingInfo(new ArrayList<>(missingInfo))
                .build();
    }

    private String fallbackAnswer(List<Map<String, Object>> agentResults) {
        return buildFallbackMerge(agentResults).getDraftAnswer();
    }

    private List<String> toStringList(JSONObject jsonObject, String key) {
        if (jsonObject.getJSONArray(key) == null) {
            return new ArrayList<>();
        }
        return jsonObject.getJSONArray(key).toJavaList(String.class);
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    public StrategyHandler<ExecuteCommandEntity, DefaultLegalFlowExecuteStrategyFactory.DynamicContext, String> get(
            ExecuteCommandEntity request,
            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        return citationVerifyNode;
    }
}
