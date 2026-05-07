package com.agent.java.model.search;

import lombok.Data;

import java.util.List;

/**
 * 搜索商品数据模型
 * 用于存储知识库中的商品信息，包含商品的基本属性和检索评分
 */
@Data
public class SearchDocument {

    /**
     * 商品唯一标识
     */
    private String id;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品描述
     */
    private String content;

    /**
     * 商品在文件系统中的路径
     */
    private String filePath;

    /**
     * 商品关键词列表（用于向量召回）
     */
    private List<String> keywords;

    /**
     * 向量召回阶段的相似度分数
     */
    private double vectorScore;

    /**
     * 重排阶段的评分
     */
    private double rerankScore;

    /**
     * 最终综合评分（向量分数和重排分数的加权平均）
     */
    private double finalScore;

    /**
     * 是否为低质量检索结果（用于动态阈值标记）
     */
    private boolean lowQuality;
}