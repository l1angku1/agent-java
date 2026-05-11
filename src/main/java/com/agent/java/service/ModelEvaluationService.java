package com.agent.java.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agent.java.model.search.EvaluationResult;
import com.agent.java.model.search.SearchDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 模型评估服务
 * 使用大模型评估检索结果的质量，计算精确率、召回率、F1分数等指标
 */
@Slf4j
@Service
public class ModelEvaluationService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final String EVALUATION_PROMPT = """
            请作为一个智能评估助手，评估以下检索结果的质量。

            用户查询: {query}

            检索到的商品(按相关性排序):
            {documents}

            请按照以下JSON格式输出评估结果:
            {
                "precision": 0.85,
                "recall": 0.75,
                "f1Score": 0.8,
                "averagePrecision": 0.82,
                "qualityLevel": "良好",
                "suggestion": "改进建议"
            }

            评估标准:
            - precision(精确率): 0-1, 表示检索结果中相关商品的比例
            - recall(召回率): 0-1, 表示查询需求被商品覆盖的程度
            - f1Score: 精确率和召回率的调和平均数
            - averagePrecision(平均精确率): 综合考虑排序质量
            - qualityLevel(质量等级): 优秀/良好/一般/较差
            """;

    /**
     * 构造函数
     *
     * @param aiService AI服务
     */
    public ModelEvaluationService(AIService aiService) {
        this.aiService = aiService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 评估检索结果质量
     *
     * @param query     用户查询
     * @param documents 检索到的商品列表
     * @return 评估结果，失败时返回null
     */
    public EvaluationResult evaluate(String query, List<SearchDocument> documents) {
        log.info("Evaluating retrieval results for query: {}", query);

        if (documents == null || documents.isEmpty()) {
            EvaluationResult result = new EvaluationResult();
            result.setPrecision(0.0);
            result.setRecall(0.0);
            result.setF1Score(0.0);
            result.setAveragePrecision(0.0);
            result.setQualityLevel("较差");
            result.setSuggestion("未找到相关商品，请尝试调整查询词");
            return result;
        }

        String documentsStr = formatDocuments(documents);
        String prompt = EVALUATION_PROMPT.replace("{query}", query).replace("{documents}", documentsStr);
        String responseContent = aiService.generate(prompt);

        if (responseContent != null) {
            log.debug("Evaluation response: {}", responseContent);

            try {
                return parseEvaluationResponse(responseContent);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse evaluation response: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * 格式化商品列表
     *
     * @param documents 商品列表
     * @return 格式化后的字符串
     */
    private String formatDocuments(List<SearchDocument> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(5, documents.size()); i++) {
            SearchDocument doc = documents.get(i);
            String contentPreview = doc.getContent() != null && doc.getContent().length() > 150
                    ? doc.getContent().substring(0, 150) + "..."
                    : doc.getContent();
            sb.append(String.format("商品%d(评分: %.2f):\n标题: %s\n内容预览: %s\n\n",
                    i + 1, doc.getFinalScore(), doc.getTitle(), contentPreview));
        }
        return sb.toString();
    }

    /**
     * 解析评估响应JSON
     *
     * @param json JSON字符串
     * @return 评估结果
     * @throws JsonProcessingException JSON解析异常
     */
    private EvaluationResult parseEvaluationResponse(String json) throws JsonProcessingException {
        String cleanedJson = cleanJsonResponse(json);
        Map<String, Object> map = objectMapper.readValue(cleanedJson, new TypeReference<Map<String, Object>>() {
        });

        EvaluationResult result = new EvaluationResult();

        if (map.containsKey("precision")) {
            result.setPrecision(getDoubleValue(map.get("precision")));
        }
        if (map.containsKey("recall")) {
            result.setRecall(getDoubleValue(map.get("recall")));
        }
        if (map.containsKey("f1Score")) {
            result.setF1Score(getDoubleValue(map.get("f1Score")));
        } else {
            result.calculateF1Score();
        }
        if (map.containsKey("averagePrecision")) {
            result.setAveragePrecision(getDoubleValue(map.get("averagePrecision")));
        }
        if (map.containsKey("qualityLevel")) {
            result.setQualityLevel(String.valueOf(map.get("qualityLevel")));
        } else {
            result.determineQualityLevel();
        }
        if (map.containsKey("suggestion")) {
            result.setSuggestion(String.valueOf(map.get("suggestion")));
        }

        return result;
    }

    /**
     * 清理JSON响应，去除markdown代码块等
     *
     * @param response 原始响应
     * @return 清理后的JSON字符串
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        int jsonStart = cleaned.indexOf("{");
        int jsonEnd = cleaned.lastIndexOf("}");

        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            cleaned = cleaned.substring(jsonStart, jsonEnd + 1);
        }

        return cleaned;
    }

    /**
     * 获取double类型的值
     *
     * @param obj 对象
     * @return double值
     */
    private double getDoubleValue(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}