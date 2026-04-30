package com.lym.domain.agent.service.execute.legalFlow.model.valobj;

import com.lym.domain.agent.model.valobj.enums.AiAgentEnumVO;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * LegalFlow LLM 节点对应的 ai_client.client_id 映射。
 *
 * 注意：
 * 这里的 clientId 必须和数据库 ai_client.client_id 保持一致。
 *
 * Armory 会把 ai_client.client_id = 2011 的客户端注册成：
 * ai_client_2011
 *
 * 所以这里不要直接写 beanName，而是通过：
 * AiAgentEnumVO.AI_CLIENT.getBeanName(clientId)
 * 生成，保持和 auto 里的获取方式一致。
 */
@Getter
@AllArgsConstructor
public enum ClientIdEnums {

    /**
     * LegalIntentNode
     */
    LEGAL_INTENT("2011", "LegalIntentNode", "法律意图识别Client"),

    /**
     * ContractReviewNode
     */
    CONTRACT_REVIEW("2012", "ContractReviewNode", "合同审查Client"),

    /**
     * LegalQaNode
     */
    LEGAL_QA("2013", "LegalQaNode", "法律问答Client"),

    /**
     * CaseSearchNode
     */
    CASE_SEARCH("2014", "CaseSearchNode", "案例检索Client"),

    /**
     * ComplianceCheckNode
     */
    COMPLIANCE_CHECK("2015", "ComplianceCheckNode", "合规检查Client"),

    /**
     * LegalDraftNode
     */
    LEGAL_DRAFT("2016", "LegalDraftNode", "法律文书草拟Client"),

    /**
     * GeneralChatNode
     */
    GENERAL_CHAT("2017", "GeneralChatNode", "普通法律对话Client"),

    /**
     * CitationVerifyNode
     */
    CITATION_VERIFY("2018", "CitationVerifyNode", "引用校验Client"),

    /**
     * RiskGateNode
     */
    RISK_GATE("2019", "RiskGateNode", "风险分级Client"),

    /**
     * AnswerGenerateNode
     */
    ANSWER_GENERATE("2020", "AnswerGenerateNode", "最终答案生成Client"),

    /**
     * MemorySummaryNode
     */
    MEMORY_SUMMARY("2021", "MemorySummaryNode", "摘要记忆Client");

    /**
     * 数据库 ai_client.client_id
     */
    private final String clientId;

    /**
     * 对应 LegalFlow 策略树节点
     */
    private final String nodeName;

    /**
     * 描述
     */
    private final String description;

    /**
     * 获取 Armory 动态注册的 ChatClient Bean 名称。
     *
     * 例如：
     * clientId = 2011
     * beanName = ai_client_2011
     */
    public String getBeanName() {
        return AiAgentEnumVO.AI_CLIENT.getBeanName(clientId);
    }
}