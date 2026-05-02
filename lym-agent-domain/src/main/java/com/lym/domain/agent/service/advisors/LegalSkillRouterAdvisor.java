package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class LegalSkillRouterAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void afterIntent(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        String intent = context.getIntentCode();
        List<String> skillPath;

        if ("contract_review".equals(intent)) {
            skillPath = Arrays.asList("legal_retrieval_skill", "contract_review_skill", "citation_verify_skill", "risk_gate_skill", "answer_generate_skill");
        } else if ("case_search".equals(intent)) {
            skillPath = Arrays.asList("legal_retrieval_skill", "case_search_skill", "citation_verify_skill", "answer_generate_skill");
        } else if ("compliance_check".equals(intent)) {
            skillPath = Arrays.asList("legal_retrieval_skill", "compliance_check_skill", "citation_verify_skill", "risk_gate_skill", "answer_generate_skill");
        } else if ("document_drafting".equals(intent)) {
            skillPath = Arrays.asList("legal_retrieval_skill", "legal_draft_skill", "citation_verify_skill", "risk_gate_skill", "answer_generate_skill");
        } else if ("legal_qa".equals(intent)) {
            skillPath = Arrays.asList("legal_retrieval_skill", "legal_qa_skill", "citation_verify_skill", "risk_gate_skill", "answer_generate_skill");
        } else {
            skillPath = Arrays.asList("general_legal_chat_skill", "answer_generate_skill");
        }

        context.setSkillPath(skillPath);

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 4,
                "Advisor[Neo4jSkillRouter]：Skill路径=" + skillPath,
                context.getSessionId());
    }

}
