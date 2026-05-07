package com.agent.java.model.search;

import lombok.Data;

/**
 * 检索质量评估结果模型
 * 用于评估检索结果的质量指标
 */
@Data
public class EvaluationResult {

    /**
     * 精确率（0-1）：检索结果中相关商品的比例
     */
    private double precision;

    /**
     * 召回率（0-1）：查询需求被商品覆盖的程度
     */
    private double recall;

    /**
     * F1分数：精确率和召回率的调和平均数
     */
    private double f1Score;

    /**
     * 平均精确率：综合考虑排序质量
     */
    private double averagePrecision;

    /**
     * 质量等级（优秀/良好/一般/较差）
     */
    private String qualityLevel;

    /**
     * 改进建议
     */
    private String suggestion;

    /**
     * 计算F1分数
     */
    public void calculateF1Score() {
        this.f1Score = (precision + recall == 0) ? 0 : 2 * precision * recall / (precision + recall);
    }

    /**
     * 根据F1分数确定质量等级
     */
    public void determineQualityLevel() {
        if (f1Score >= 0.8) {
            this.qualityLevel = "优秀";
            this.suggestion = "检索结果质量很高，回答可以直接基于检索结果生成";
        } else if (f1Score >= 0.6) {
            this.qualityLevel = "良好";
            this.suggestion = "检索结果质量较好，可以作为回答的主要参考";
        } else if (f1Score >= 0.4) {
            this.qualityLevel = "一般";
            this.suggestion = "检索结果质量一般，建议结合其他来源信息";
        } else {
            this.qualityLevel = "较差";
            this.suggestion = "检索结果质量较差，建议扩大搜索范围或调整查询词";
        }
    }
}