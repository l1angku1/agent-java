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
     * 提取的关键词列表（3-5个，仅用于语义匹配，不含动词）
     */
    private List<String> keywords;

    /**
     * 识别的实体列表（如产品、人物、地点等名词）
     */
    private List<String> entities;

    /**
     * 是否需要上下文理解
     */
    private boolean requiresContext;

    /**
     * 优化后的查询语句（用于召回，不含过滤条件和动词）
     */
    private String rewrittenQuery;
    
    /**
     * LLM 生成的过滤条件表达式
     * 格式示例: "price>=100 && price<1000 && stock>0"
     * 支持的字段: price, stock, salesVolume, shareCount
     * 支持的运算符: ==, !=, <, <=, >, >=
     * 逻辑运算符: && (与), || (或)
     */
    private String filterExpression;
}