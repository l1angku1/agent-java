package com.agent.java.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.agent.java.model.search.SearchDocument;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 模型重排服务
 * 使用大模型对召回的文档进行相关性评分和排序
 */
@Slf4j
@Service
public class ModelRerankService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final String RERANK_PROMPT = """
            请作为一个智能排序助手，对以下文档与用户查询的相关性进行评分。

            用户查询: {query}

            文档列表:
            {documents}

            请按照以下JSON格式输出评分结果, 分数范围为0-10分:
            [
                {"documentId": "文档ID", "score": 5.5, "reason": "评分理由"}
            ]

            评分标准:
            - 10分: 文档内容完全匹配查询需求
            - 8-9分: 文档内容高度相关
            - 6-7分: 文档内容部分相关
            - 4-5分: 文档内容有一定相关性
            - 0-3分: 文档内容基本不相关

            请确保每个文档都有评分和理由。
            """;

    /**
     * 构造函数
     * 
     * @param aiService AI服务
     */
    public ModelRerankService(AIService aiService) {
        this.aiService = aiService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 对文档进行重排
     * 
     * @param query     用户查询
     * @param documents 待排序的文档列表
     * @return 重排后的文档列表
     */
    public List<SearchDocument> rerank(String query, List<SearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        log.info("Reranking {} documents for query: {}", documents.size(), query);

        String documentsStr = formatDocuments(documents);
        String prompt = RERANK_PROMPT.replace("{query}", query).replace("{documents}", documentsStr);
        String responseContent = aiService.generate(prompt);

        Map<String, Double> scoresMap = new HashMap<>();

        if (responseContent != null) {
            log.debug("Rerank response: {}", responseContent);

            try {
                List<Map<String, Object>> results = objectMapper.readValue(responseContent,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

                for (Map<String, Object> result : results) {
                    String docId = String.valueOf(result.get("documentId"));
                    Object scoreObj = result.get("score");
                    double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
                    scoresMap.put(docId, score / 10.0);
                }
            } catch (JsonProcessingException e) {
                log.error("Failed to parse rerank response: {}", e.getMessage());
                throw new RuntimeException("Failed to parse rerank response: " + e.getMessage());
            }
        } else {
            throw new RuntimeException("AI service returned empty response for reranking query: " + query);
        }

        final Map<String, Double> finalScores = scoresMap;
        List<SearchDocument> reranked = new ArrayList<>(documents);
        reranked.sort((a, b) -> {
            double scoreA = finalScores.getOrDefault(a.getId(), 0.0);
            double scoreB = finalScores.getOrDefault(b.getId(), 0.0);
            return Double.compare(scoreB, scoreA);
        });

        for (SearchDocument doc : reranked) {
            doc.setRerankScore(scoresMap.getOrDefault(doc.getId(), 0.0));
            doc.setFinalScore(0.5 * doc.getVectorScore() + 0.5 * doc.getRerankScore());
        }

        log.debug("Reranking completed, top score: {}", reranked.isEmpty() ? 0 : reranked.get(0).getFinalScore());
        return reranked;
    }

    /**
     * 格式化文档列表
     * 
     * @param documents 文档列表
     * @return 格式化后的字符串
     */
    private String formatDocuments(List<SearchDocument> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            SearchDocument doc = documents.get(i);
            String contentPreview = doc.getContent() != null && doc.getContent().length() > 200
                    ? doc.getContent().substring(0, 200) + "..."
                    : doc.getContent();
            sb.append(String.format("文档%d(ID:%s):\n标题: %s\n内容预览: %s\n\n",
                    i + 1, doc.getId(), doc.getTitle(), contentPreview));
        }
        return sb.toString();
    }
}