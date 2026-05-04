package com.lym.trigger.tool;

import com.lym.domain.agent.adapter.repository.ILegalCodeSearchRepository;
import com.lym.domain.agent.model.entity.LegalCodeSearchCommandEntity;
import com.lym.domain.agent.model.entity.LegalCodeSearchResultEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LegalWebCodeSearchTool {

    private final ILegalCodeSearchRepository legalCodeSearchRepository;

    @Tool(
            name = "legal_web_code_search",
            description = """
                    法律网页法条检索工具。用于根据法律关键词和法典编码，从配置好的法律网页中检索相关法条原文。

                    使用场景：
                    - 用户询问法律责任、赔偿、诉讼、劳动、合同、继承、犯罪、刑罚等问题时使用。
                    - 用户明确提到“第X条”或具体法条编号时，应优先使用 articleNo 检索。
                    - 用户没有明确法条编号时，应先把用户问题转化为 2 到 8 个法律检索关键词，再调用本工具。

                    lawCodes 选择规则：
                    - 枪击、走私、诈骗、盗窃、故意伤害、非法持有枪支、犯罪、刑罚：criminal_law
                    - 合同、侵权、婚姻、继承、物权、人格权、民事责任：civil_code
                    - 起诉、管辖、执行、证据、上诉、财产保全、民事诉讼程序：civil_procedure_law
                    - 劳动合同、工资、辞退、经济补偿、双倍工资：labor_contract_law
                    - 如果一个问题同时涉及实体权利和诉讼程序，可以同时传多个 lawCodes，例如：civil_code,civil_procedure_law

                    返回说明：
                    - 返回结果包含 lawCode、lawName、articleNo、content、matchType、score。
                    - 如果返回为空，表示当前配置网页中未检索到明确法条依据。
                    - 回答法律问题时应优先基于本工具返回的 content，不要编造法条。
                    """
    )
    public List<LegalCodeSearchResultEntity> search(
            @ToolParam(
                    description = """
                            法律检索关键词，不一定是用户原文。
                            应由模型从用户问题中提取 2 到 8 个法律关键词，用空格分隔。
                            示例：
                            - 不签劳动合同 双倍工资 用人单位
                            - 遗产 继承 财产纠纷 起诉
                            - 枪击 走私 非法持有枪支
                            """,
                    required = true
            )
            String keywords,

            @ToolParam(
                    description = """
                            要查询的法典编码，多个用英文逗号分隔。
                            可选值：
                            - criminal_law：刑法
                            - civil_code：民法典
                            - civil_procedure_law：民事诉讼法
                            - labor_contract_law：劳动合同法

                            如果不确定，可以传多个，例如 civil_code,civil_procedure_law。
                            """,
                    required = true
            )
            String lawCodes,

            @ToolParam(
                    description = """
                            法条编号。没有明确法条编号时传空字符串。
                            示例：82、第八十二条、第39条。
                            """,
                    required = false
            )
            String articleNo,

            @ToolParam(
                    description = "返回数量，默认 5，最大 20。",
                    required = false
            )
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