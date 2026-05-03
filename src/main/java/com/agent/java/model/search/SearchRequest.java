package com.agent.java.model.search;

import lombok.Data;

/**
 * AI搜索请求模型
 * 用于接收用户的搜索请求参数
 */
@Data
public class SearchRequest {

    /**
     * 用户查询内容
     */
    private String query;

    /**
     * 返回结果数量（默认10）
     */
    private int topK = 10;

    /**
     * 是否启用智能重排（默认启用）
     */
    private boolean enableRerank = true;

    /**
     * 是否启用质量评估（默认启用）
     */
    private boolean enableEvaluation = true;

    /**
     * 用户ID（可选，用于个性化搜索）
     */
    private String userId;

    /**
     * 会话ID（可选，用于会话级记忆）
     */
    private String sessionId;

    /**
     * 是否启用记忆功能（默认启用）
     */
    private boolean enableMemory = true;

    /**
     * 构造函数：仅包含查询内容
     */
    public SearchRequest(String query) {
        this.query = query;
    }
}