package com.agent.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * 语义缓存配置类
 */
@Data
@Component
public class SemanticCacheConfig {

    /** 是否启用语义缓存 */
    @Value("${semantic.cache.enabled:true}")
    private boolean enabled;

    /** 语义相似度阈值，超过此阈值则认为两个查询语义相似 */
    @Value("${semantic.cache.similarity-threshold:0.85}")
    private double similarityThreshold;

    /** 缓存最大容量 */
    @Value("${semantic.cache.max-size:1000}")
    private int maxCacheSize;

    /** 缓存过期时间（小时） */
    @Value("${semantic.cache.expire-hours:24}")
    private int expireHours;
}