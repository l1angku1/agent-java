package com.agent.java.model.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 语义缓存条目数据模型
 * 用于存储查询及其对应的响应结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticCacheEntry {

    /**
     * 规范化后的查询文本
     */
    private String query;

    /**
     * 查询的向量表示
     */
    private double[] queryEmbedding;

    /**
     * 缓存的响应结果
     */
    private String response;

    /**
     * 缓存创建时间戳
     */
    private long timestamp;

    /**
     * 缓存命中次数
     */
    private int hitCount;
}