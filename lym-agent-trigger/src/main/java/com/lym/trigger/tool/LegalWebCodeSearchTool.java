package com.lym.trigger.tool;

import com.lym.domain.agent.adapter.repository.ILegalCodeSearchRepository;
import com.lym.domain.agent.model.entity.LegalCodeSearchCommandEntity;
import com.lym.domain.agent.model.entity.LegalCodeSearchResultEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 提供给大模型调用的法律网页法条检索 Tool。
 */
@Component
@RequiredArgsConstructor
public class LegalWebCodeSearchTool {

    private final ILegalCodeSearchRepository legalCodeSearchRepository;

    @Tool(
            name = "legal_web_code_search",
            description = "根据法律关键词和法典文档编码，从配置网页中检索法条。"
    )
    public List<LegalCodeSearchResultEntity> search(
            @ToolParam(description = "大模型提取后的法律关键词，例如：枪击 走私 非法持有枪支", required = true)
            String keywords,

            @ToolParam(description = "法典编码，多个用英文逗号分隔，例如：criminal_law,civil_code,civil_procedure_law", required = true)
            String lawCodes,

            @ToolParam(description = "法条编号，例如：82、第八十二条、第39条；没有则传空字符串", required = false)
            String articleNo,

            @ToolParam(description = "返回数量，默认5，最大20", required = false)
            Integer topK
    ) {
        return legalCodeSearchRepository.searchLegalCode(
                LegalCodeSearchCommandEntity.builder()
                        .keywords(keywords)
                        .lawCodes(lawCodes)
                        .articleNo(articleNo)
                        .topK(topK)
                        .build()
        );
    }
}