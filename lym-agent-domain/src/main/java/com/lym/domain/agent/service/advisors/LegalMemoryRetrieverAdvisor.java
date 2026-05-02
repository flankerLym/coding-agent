package com.lym.domain.agent.service.advisors;

import com.lym.domain.agent.model.entity.ExecuteCommandEntity;
import com.lym.domain.agent.service.execute.legalFlow.LegalFlowSseUtils;
import com.lym.domain.agent.service.execute.legalFlow.factory.DefaultLegalFlowExecuteStrategyFactory;
import com.lym.domain.agent.service.execute.legalFlow.model.LegalMemoryHit;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class LegalMemoryRetrieverAdvisor implements LegalFlowAdvisor {

    @Override
    public int order() {
        return 50;
    }

    @Override
    public void afterIntent(ExecuteCommandEntity request,
                            DefaultLegalFlowExecuteStrategyFactory.DynamicContext context) {
        List<LegalMemoryHit> hits = new ArrayList<>();
        hits.add(LegalMemoryHit.builder()
                .memoryId("mem_legal_common_001")
                .title("法律助手回答边界")
                .content("法律助手回答应基于用户材料和可核验依据；依据不足时应提示补充材料，不应给出确定法律结论。")
                .sourceType("system_policy")
                .score(0.92)
                .build());

        if ("contract_review".equals(context.getIntentCode())) {
            hits.add(LegalMemoryHit.builder()
                    .memoryId("mem_contract_review_001")
                    .title("合同审查关注点")
                    .content("合同审查应重点关注主体、标的、付款、履行期限、违约责任、解除、争议解决、保密和知识产权条款。")
                    .sourceType("legal_knowledge")
                    .score(0.88)
                    .build());
        }

        context.setMemoryHits(hits);

        LegalFlowSseUtils.sendAnalysis(context.getEmitter(), 6,
                "Advisor[PgVectorRetriever]：命中记忆=" + hits.size(),
                context.getSessionId());
    }

}
