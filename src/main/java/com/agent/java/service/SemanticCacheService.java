package com.agent.java.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agent.java.config.OllamaConfig;
import com.agent.java.config.SemanticCacheConfig;
import com.agent.java.model.cache.SemanticCacheEntry;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.message.TextBlock;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语义缓存服务
 * 使用向量相似度匹配实现语义级别的查询缓存
 * 当两个查询语义相似时，可以复用之前的响应结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final OllamaConfig ollamaConfig;
    private final SemanticCacheConfig cacheConfig;
    private Cache<String, SemanticCacheEntry> cache;
    private OllamaTextEmbedding embeddingModel;

    @PostConstruct
    public void init() {
        embeddingModel = OllamaTextEmbedding.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(ollamaConfig.getModelName())
                .dimensions(ollamaConfig.getDimensions())
                .build();

        cache = Caffeine.newBuilder()
                .maximumSize(cacheConfig.getMaxCacheSize())
                .expireAfterWrite(Duration.ofHours(cacheConfig.getExpireHours()))
                .removalListener(
                        (key, entry, cause) -> log.debug("Semantic cache evicted: key={}, cause={}", key, cause))
                .recordStats()
                .build();
    }

    /**
     * 根据语义相似度查询缓存
     * 
     * @param query 用户查询
     * @return 缓存的响应结果，未命中返回null
     */
    public String get(String query) {
        if (!cacheConfig.isEnabled() || query == null || query.trim().isEmpty()) {
            return null;
        }

        String normalizedQuery = normalizeQuery(query);

        // 先尝试精确匹配
        SemanticCacheEntry exactEntry = cache.getIfPresent(normalizedQuery);
        if (exactEntry != null) {
            exactEntry.setHitCount(exactEntry.getHitCount() + 1);
            log.debug("Semantic cache exact hit for query: {}", query);
            return exactEntry.getResponse();
        }

        // 尝试语义相似匹配
        return getBySemanticSimilarity(query, normalizedQuery);
    }

    /**
     * 基于语义相似度查询缓存
     */
    private String getBySemanticSimilarity(String originalQuery, String normalizedQuery) {
        double[] queryEmbedding = embedQuery(normalizedQuery);
        if (queryEmbedding == null) {
            return null;
        }

        double bestSimilarity = 0;
        SemanticCacheEntry bestEntry = null;

        // 遍历缓存查找最相似的条目
        for (Map.Entry<String, SemanticCacheEntry> entry : cache.asMap().entrySet()) {
            SemanticCacheEntry cacheEntry = entry.getValue();
            if (cacheEntry.getQueryEmbedding() != null) {
                double similarity = calculateCosineSimilarity(queryEmbedding, cacheEntry.getQueryEmbedding());
                if (similarity >= cacheConfig.getSimilarityThreshold() && similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestEntry = cacheEntry;
                }
            }
        }

        if (bestEntry != null) {
            bestEntry.setHitCount(bestEntry.getHitCount() + 1);
            log.info("Semantic cache hit with similarity: {:.4f}, query: {}", bestSimilarity, originalQuery);
            return bestEntry.getResponse();
        }

        return null;
    }

    /**
     * 存储查询结果到缓存
     * 
     * @param query    用户查询
     * @param response 响应结果
     */
    public void put(String query, String response) {
        if (!cacheConfig.isEnabled() || query == null || query.trim().isEmpty() || response == null) {
            return;
        }

        String normalizedQuery = normalizeQuery(query);

        // 检查是否已存在相同查询的缓存
        if (cache.getIfPresent(normalizedQuery) != null) {
            log.debug("Cache entry already exists for query: {}", query);
            return;
        }

        // 向量化查询
        double[] queryEmbedding = embedQuery(normalizedQuery);
        if (queryEmbedding == null) {
            log.warn("Failed to embed query for caching: {}", query);
            return;
        }

        SemanticCacheEntry entry = SemanticCacheEntry.builder()
                .query(normalizedQuery)
                .queryEmbedding(queryEmbedding)
                .response(response)
                .timestamp(System.currentTimeMillis())
                .hitCount(0)
                .build();

        cache.put(normalizedQuery, entry);
        log.debug("Semantic cache added for query: {}", query);
    }

    /**
     * 删除指定查询的缓存
     */
    public void remove(String query) {
        if (!cacheConfig.isEnabled() || query == null) {
            return;
        }
        cache.invalidate(normalizeQuery(query));
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        cache.invalidateAll();
        log.info("Semantic cache cleared");
    }

    /**
     * 向量化查询文本
     */
    private double[] embedQuery(String query) {
        try {
            TextBlock textBlock = TextBlock.builder().text(query).build();
            return embeddingModel.embed(textBlock).block();
        } catch (Exception e) {
            log.error("Failed to embed query: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 规范化查询文本
     */
    private String normalizeQuery(String query) {
        if (query == null) {
            return "";
        }
        // 去除首尾空格，转换为小写
        return query.trim().toLowerCase();
    }

    /**
     * 计算两个向量的余弦相似度
     */
    private double calculateCosineSimilarity(double[] vec1, double[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) {
            return 0.0;
        }

        double dotProduct = 0;
        double norm1 = 0;
        double norm2 = 0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += Math.pow(vec1[i], 2);
            norm2 += Math.pow(vec2[i], 2);
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}