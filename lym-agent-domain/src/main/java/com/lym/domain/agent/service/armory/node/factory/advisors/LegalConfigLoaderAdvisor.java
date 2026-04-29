package com.lym.domain.agent.service.armory.node.factory.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalSkillConfig;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalToolConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegalConfigLoaderAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void afterIntent(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        List<LegalSkillConfig> skills = new ArrayList<>();
        if (context.getSkillPath() != null) {
            for (String skillCode : context.getSkillPath()) {
                skills.add(LegalSkillConfig.builder()
                        .skillCode(skillCode)
                        .skillName(skillCode)
                        .skillType(resolveType(skillCode))
                        .modelId(resolveModel(skillCode))
                        .promptId("prompt_" + skillCode)
                        .description("默认配置，后续替换为 MySQL 配置")
                        .build());
            }
        }

        List<LegalToolConfig> tools = new ArrayList<>();
        tools.add(LegalToolConfig.builder()
                .mcpId("legal_retrieval_mcp")
                .toolName("legal.pgvector.search")
                .toolDesc("检索法律知识和历史记忆")
                .riskLevel("low")
                .required(true)
                .build());

        context.setSkillConfigs(skills);
        context.setMcpTools(tools);

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 5,
                "Advisor[MySQLConfigLoader]：skill=" + skills.size() + " mcpTools=" + tools.size(),
                context.getSessionId());
    }

    private String resolveType(String skillCode) {
        if (skillCode.contains("retrieval")) return "retrieval";
        if (skillCode.contains("citation")) return "verify";
        if (skillCode.contains("risk")) return "risk";
        if (skillCode.contains("answer")) return "generate";
        return "analysis";
    }

    private String resolveModel(String skillCode) {
        if (skillCode.contains("citation")) return "legal_verify_model";
        if (skillCode.contains("risk")) return "legal_fast_model";
        if (skillCode.contains("answer")) return "legal_answer_model";
        if (skillCode.contains("retrieval")) return "legal_embedding_model";
        return "legal_reason_model";
    }

}
