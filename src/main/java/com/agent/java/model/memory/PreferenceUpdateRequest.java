package com.agent.java.model.memory;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 用户偏好更新请求模型
 * <p>
 * 用于接收用户偏好更新的请求参数，支持增量更新用户偏好信息。
 * </p>
 */
@Data
public class PreferenceUpdateRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 要添加的品牌列表
     */
    private List<String> addBrands;

    /**
     * 要移除的品牌列表
     */
    private List<String> removeBrands;

    /**
     * 要添加的类别列表
     */
    private List<String> addCategories;

    /**
     * 要移除的类别列表
     */
    private List<String> removeCategories;

    /**
     * 要添加的关键词权重映射
     * <p>key: 关键词, value: 权重增量</p>
     */
    private Map<String, Double> addKeywordWeights;

    /**
     * 最低价格
     */
    private Double minPrice;

    /**
     * 最高价格
     */
    private Double maxPrice;

    /**
     * 沟通风格
     */
    private String communicationStyle;

    /**
     * 相关性阈值
     */
    private Double relevanceThreshold;
}
