package com.agent.java.model.search;

import lombok.Data;

import java.util.List;

/**
 * 搜索文档数据模型
 * 用于存储知识库中的文档信息，包含文档的基本属性和检索评分
 */
@Data
public class SearchDocument {

    /**
     * 文档唯一标识
     */
    private String id;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档内容
     */
    private String content;

    /**
     * 文档在文件系统中的路径
     */
    private String filePath;

    /**
     * 文档关键词列表（用于向量召回）
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
}