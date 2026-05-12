package com.agent.java.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.SearchDocument;
import com.agent.java.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 模型重排服务
 * 使用大模型对召回的商品进行相关性评分和排序
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRerankService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final String RERANK_PROMPT = """
            请作为一个智能排序助手，对以下商品与用户查询的相关性进行评分。

            用户查询: {query}

            商品列表:
            {documents}

            请按照以下JSON格式输出评分结果, 分数范围为0-10分:
            [
                {"documentId": "商品ID", "score": 5.5, "reason": "评分理由"}
            ]

            评分标准:
            - 10分: 商品内容完全匹配查询需求
            - 8-9分: 商品内容高度相关
            - 6-7分: 商品内容部分相关
            - 4-5分: 商品内容有一定相关性
            - 0-3分: 商品内容基本不相关

            请确保每个商品都有评分和理由。
            """;

    private static final String PREFERENCE_RERANK_PROMPT = """
            请作为一个智能排序助手，对以下商品与用户查询的相关性进行评分，评分时请优先考虑用户的偏好信息。

            用户查询: {query}

            用户偏好:
            - 偏好品牌: {preferredBrands}
            - 偏好类目: {preferredCategories}
            - 价格范围: {priceRange}
            - 关键词偏好: {keywordWeights}

            商品列表:
            {documents}

            请按照以下JSON格式输出评分结果, 分数范围为0-10分:
            [
                {"documentId": "商品ID", "score": 5.5, "reason": "评分理由"}
            ]

            评分标准:
            - 10分: 商品内容完全匹配查询需求且高度符合用户偏好
            - 8-9分: 商品内容高度相关且符合用户偏好
            - 6-7分: 商品内容部分相关或部分符合用户偏好
            - 4-5分: 商品内容有一定相关性
            - 0-3分: 商品内容基本不相关

            请确保每个商品都有评分和理由。
            """;

    /**
     * 对商品进行重排
     * 
     * @param query     用户查询
     * @param documents 待排序的商品列表
     * @return 重排的商品列表
     */
    public List<SearchDocument> rerank(String query, List<SearchDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        log.info("Reranking {} goods for query: {}", documents.size(), query);

        String documentsStr = formatDocuments(documents);
        String prompt = RERANK_PROMPT.replace("{query}", query).replace("{documents}", documentsStr);
        String responseContent = aiService.generate(prompt);

        Map<String, Double> scoresMap = parseRerankResponse(responseContent, query);
        return applyRerankScores(documents, scoresMap);
    }

    /**
     * 对商品进行重排（带用户偏好）
     * 
     * @param query      用户查询
     * @param documents  待排序的商品列表
     * @param preference 用户偏好
     * @return 重排后的商品列表
     */
    public List<SearchDocument> rerank(String query, List<SearchDocument> documents, UserPreference preference) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }

        log.info("Reranking {} documents with preference for user: {}", documents.size(), preference.getUserId());

        String documentsStr = formatDocuments(documents);
        String prompt = buildPreferenceRerankPrompt(query, documentsStr, preference);
        String responseContent = aiService.generate(prompt);

        Map<String, Double> scoresMap = parseRerankResponse(responseContent, query);
        return applyRerankScores(documents, scoresMap);
    }

    /**
     * 解析重排响应
     */
    private Map<String, Double> parseRerankResponse(String responseContent, String query) {
        Map<String, Double> scoresMap = new HashMap<>();

        if (responseContent != null) {
            log.debug("Rerank response: {}", responseContent);

            try {
                String cleanJson = JsonUtils.stripMarkdownCodeBlock(responseContent);
                List<Map<String, Object>> results = objectMapper.readValue(cleanJson,
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

        return scoresMap;
    }

    /**
     * 应用重排分数
     */
    private List<SearchDocument> applyRerankScores(List<SearchDocument> documents, Map<String, Double> scoresMap) {
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
     * 构建带偏好的重排提示词
     */
    private String buildPreferenceRerankPrompt(String query, String documentsStr, UserPreference preference) {
        String preferredBrands = preference.getPreferredBrands().isEmpty()
                ? "无"
                : String.join(", ", preference.getPreferredBrands());

        String preferredCategories = preference.getPreferredCategories().isEmpty()
                ? "无"
                : String.join(", ", preference.getPreferredCategories());

        String priceRange = preference.getPriceRange().isEmpty()
                ? "无"
                : preference.getPriceRange().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(", "));

        String keywordWeights = preference.getKeywordWeights().isEmpty()
                ? "无"
                : preference.getKeywordWeights().entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(5)
                        .map(e -> e.getKey() + "(" + String.format("%.2f", e.getValue()) + ")")
                        .collect(Collectors.joining(", "));

        return PREFERENCE_RERANK_PROMPT
                .replace("{query}", query)
                .replace("{preferredBrands}", preferredBrands)
                .replace("{preferredCategories}", preferredCategories)
                .replace("{priceRange}", priceRange)
                .replace("{keywordWeights}", keywordWeights)
                .replace("{documents}", documentsStr);
    }

    /**
     * 格式化商品列表
     * 
     * @param documents 商品列表
     * @return 格式化后的字符串
     */
    private String formatDocuments(List<SearchDocument> documents) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            SearchDocument doc = documents.get(i);
            String contentPreview = doc.getContent() != null && doc.getContent().length() > 200
                    ? doc.getContent().substring(0, 200) + "..."
                    : doc.getContent();
            sb.append(String.format("商品%d(ID:%s):\n标题: %s\n内容预览: %s\n\n",
                    i + 1, doc.getId(), doc.getTitle(), contentPreview));
        }
        return sb.toString();
    }
}