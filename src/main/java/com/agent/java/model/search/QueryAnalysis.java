package com.agent.java.model.search;

import lombok.Data;

import java.util.List;

/**
 * 查询分析结果模型
 * 用于存储大模型对用户查询的分析结果
 */
@Data
public class QueryAnalysis {

    /**
     * 原始用户查询
     */
    private String originalQuery;

    /**
     * 意图类型（问答、事实查询、对比、建议、其他）
     */
    private String intentType;

    /**
     * 提取的关键词列表（3-5个）
     */
    private List<String> keywords;

    /**
     * 识别的实体列表（如产品、人物、地点等）
     */
    private List<String> entities;

    /**
     * 是否需要上下文理解
     */
    private boolean requiresContext;

    /**
     * 优化后的查询语句（用于召回）
     */
    private String rewrittenQuery;
}