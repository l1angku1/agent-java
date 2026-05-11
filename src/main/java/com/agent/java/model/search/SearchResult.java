package com.agent.java.model.search;

import lombok.Data;

import java.util.List;

/**
 * AI搜索结果模型
 * 用于返回搜索结果和相关统计信息
 */
@Data
public class SearchResult {

    /**
     * 用户原始查询
     */
    private String query;

    /**
     * AI生成的回答
     */
    private String answer;

    /**
     * 检索到的商品列表（按相关性排序）
     */
    private List<SearchDocument> documents;

    /**
     * 商品数量
     */
    private int goodsCount;

    /**
     * 检索质量评估结果
     */
    private EvaluationResult evaluation;

    /**
     * 质量等级
     */
    private String qualityLevel;

    /**
     * F1分数
     */
    private double f1Score;

    /**
     * 是否个性化搜索
     */
    private boolean personalized;

    /**
     * 是否命中缓存
     */
    private boolean cacheHit;

    /**
     * 召回阶段耗时（毫秒）
     */
    private long recallTime;

    /**
     * 重排阶段耗时（毫秒）
     */
    private long rerankTime;

    /**
     * 总耗时（毫秒）
     */
    private long totalTime;
}