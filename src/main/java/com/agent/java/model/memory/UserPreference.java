package com.agent.java.model.memory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 用户偏好模型
 * <p>
 * 用于存储和管理用户的个性化偏好信息，包括品牌偏好、类别偏好、价格范围、关键词权重等。
 * 这些偏好信息用于增强搜索结果的个性化推荐效果。
 * </p>
 */
@Data
public class UserPreference {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 偏好品牌列表
     */
    private List<String> preferredBrands;

    /**
     * 偏好类别列表
     */
    private List<String> preferredCategories;

    /**
     * 价格范围（minPrice, maxPrice）
     */
    private Map<String, Double> priceRange;

    /**
     * 关键词权重映射
     * <p>key: 关键词, value: 权重(0.0-1.0)</p>
     */
    private Map<String, Double> keywordWeights;

    /**
     * 近期搜索关键词列表
     */
    private List<String> recentSearchedKeywords;

    /**
     * 沟通风格（如：professional, friendly, casual等）
     */
    private String communicationStyle;

    /**
     * 最后更新时间（字符串格式）
     */
    private String lastUpdated;

    /**
     * 偏好置信度（0.0-1.0）
     * <p>表示偏好信息的可靠程度</p>
     */
    private double confidence;

    /**
     * 相关性阈值
     * <p>用于过滤低相关度的搜索结果</p>
     */
    private double relevanceThreshold;

    /**
     * 默认构造函数
     * <p>初始化所有集合类型属性为空集合，设置默认沟通风格为"professional"</p>
     */
    public UserPreference() {
        this.preferredBrands = new ArrayList<>();
        this.preferredCategories = new ArrayList<>();
        this.priceRange = new HashMap<>();
        this.keywordWeights = new HashMap<>();
        this.recentSearchedKeywords = new ArrayList<>();
        this.communicationStyle = "professional";
        this.lastUpdated = "";
        this.confidence = 0.5;
        this.relevanceThreshold = 0.5;
    }

    /**
     * 构造函数
     * <p>创建指定用户ID的偏好对象</p>
     *
     * @param userId 用户ID
     */
    public UserPreference(String userId) {
        this();
        this.userId = userId;
    }
}
