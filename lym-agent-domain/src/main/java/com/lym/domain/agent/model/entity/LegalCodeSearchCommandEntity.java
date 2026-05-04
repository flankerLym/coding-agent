package com.lym.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 法条网页检索命令。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegalCodeSearchCommandEntity {

    /**
     * 大模型提取后的法律关键词，不一定是用户原文。
     * 例如：枪击 走私 非法持有枪支
     */
    private String keywords;

    /**
     * 要查询的法典编码，多个用英文逗号分隔。
     * 例如：criminal_law,civil_code,civil_procedure_law
     */
    private String lawCodes;

    /**
     * 法条编号。
     * 例如：82、第八十二条、第39条。
     */
    private String articleNo;

    /**
     * 返回数量。
     */
    private Integer topK;
}