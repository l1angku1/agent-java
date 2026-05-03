package com.agent.java.model.search;

import java.util.List;

import lombok.Data;

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
     * 检索到的文档列表（按相关性排序）
     */
    private List<SearchDocument> documents;

    /**
     * 检索质量评估结果
     */
    private EvaluationResult evaluation;

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