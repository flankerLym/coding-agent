package com.lym.domain.agent.service.execute.legalFlow.node.step2.business;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalDraftResult;
import com.lym.domain.agent.service.execute.legalFlow.model.skills.LegalSkillResource;
import com.lym.domain.agent.service.execute.legalFlow.model.valobj.ClientIdEnums;
import com.lym.domain.agent.service.execute.legalFlow.node.step2.LegalBusinessNodeSupport;

import com.lym.domain.agent.service.execute.legalFlow.utils.LegalSkillResourceLoader;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContractReviewNode extends LegalBusinessNodeSupport {

    private static final String DEFAULT_SKILL_ID = "contract_review";

    private static final int MAX_SKILL_SECTION_LENGTH = 4000;

    @Resource
    private LegalSkillResourceLoader legalSkillResourceLoader;

    @Override
    protected String nodeName() {
        return "ContractReviewNode";
    }

    @Override
    protected String draftType() {
        return "contract_review";
    }

    @Override
    protected ClientIdEnums clientIdEnums() {
        return ClientIdEnums.CONTRACT_REVIEW;
    }

    /**
     * 以后如果要换 Skill，只改这里即可。
     *
     * 例如：
     * return "contract_review_v2";
     */
    protected String skillId() {
        return DEFAULT_SKILL_ID;
    }

    @Override
    protected String systemPrompt() {
        LegalSkillResource skillResource = legalSkillResourceLoader.load(skillId());

        if (skillResource.hasSystemPrompt()) {
            return skillResource.getSystemPrompt();
        }

        return defaultSystemPrompt();
    }

    @Override
    protected String buildUserPrompt(ExecuteCommandEntity request,
                                     DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String basePrompt = super.buildUserPrompt(request, context);

        LegalSkillResource skillResource = legalSkillResourceLoader.load(skillId());

        if (!skillResource.isAvailable()) {
            return basePrompt;
        }

        StringBuilder prompt = new StringBuilder(basePrompt);

        prompt.append("\n\n==================== 当前装配 Skill ====================\n");
        prompt.append("skill_id: ").append(skillResource.getSkillId()).append("\n");

        appendSkillSection(prompt, "skill.md", skillResource.getSkillMarkdown());
        appendSkillSection(prompt, "metadata.yaml", skillResource.getMetadataYaml());
        appendSkillSection(prompt, "reference.yaml", skillResource.getReferenceYaml());
        appendSkillSection(prompt, "script.py", skillResource.getScriptPython());

        prompt.append("\n\n使用要求：\n");
        prompt.append("1. 优先遵守 metadata.yaml 中的 system_prompt。\n");
        prompt.append("2. 使用 reference.yaml 作为合同审查流程路由和风险检查参考。\n");
        prompt.append("3. skill.md 作为该 Skill 的能力说明。\n");
        prompt.append("4. script.py 仅作为规则脚本参考，当前 Java 节点不直接执行 Python。\n");
        prompt.append("5. 最终仍然必须严格输出 JSON，不要输出额外解释。\n");

        return prompt.toString();
    }

    private void appendSkillSection(StringBuilder prompt, String title, String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }

        prompt.append("\n\n-------------------- ")
                .append(title)
                .append(" --------------------\n")
                .append(limit(content, MAX_SKILL_SECTION_LENGTH));
    }

    private String limit(String content, int maxLength) {
        if (content == null) {
            return "";
        }

        if (content.length() <= maxLength) {
            return content;
        }

        return content.substring(0, maxLength)
                + "\n\n[内容过长，已截断，原始长度="
                + content.length()
                + "]";
    }

    private String defaultSystemPrompt() {
        return """
                你是合同审查 Agent。基于用户问题、当前子任务、最近上下文、Skill路径、MCP工具和检索材料，识别合同风险并生成结构化草稿。
                要求：不得编造合同条款、事实或法律依据；必须区分已知事实和需要补充的信息；重点关注付款、违约责任、解除、争议解决、保密、知识产权、管辖、期限、交付验收等。
                输出 JSON：{"draft_type":"contract_review","draft_answer":"","key_findings":[],"risk_points":[],"missing_info":[]}
                """;
    }

    @Override
    protected String fallbackJson() {
        return "{\"draft_type\":\"contract_review\",\"draft_answer\":\"合同审查草稿：建议重点关注付款、违约责任、解除条款、争议解决、保密和知识产权条款。\",\"key_findings\":[\"当前为合同审查 Agent 草稿。\"],\"risk_points\":[],\"missing_info\":[\"如需更准确分析，请补充完整合同文本。\"]}";
    }

    @Override
    public String apply(ExecuteCommandEntity request,
                        DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) throws Exception {
        LegalDraftResult draftResult = callAndParseDraft(request, context);

        LegalFlowSseUtils.sendExecution(
                context.getEmitter(),
                5,
                "ContractReviewNode(openAiChatClient)：合同审查草稿生成完成。",
                context.getSessionId()
        );

        log.info("ContractReviewNode completed, skillId={}, draftType={}, answer={}",
                skillId(), draftResult.getDraftType(), draftResult.getDraftAnswer());

        return afterDraft(request, context, draftResult);
    }
}