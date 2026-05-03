package com.agent.java.service;

import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.QueryAnalysis;
import com.agent.java.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 查询解析服务
 * 使用大模型分析用户查询，提取意图、关键词、实体等信息
 */
@Slf4j
@Service
public class QueryParserService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final String ANALYSIS_PROMPT = """
            你是一个智能查询分析助手。请分析用户的查询并返回结构化的JSON结果。

            用户查询: {query}

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["关键词1", "关键词2", "关键词3"],
                "entities": ["实体1", "实体2"],
                "requiresContext": false,
                "rewrittenQuery": "优化后的查询语句"
            }

            注意事项:
            1. 关键词数量控制在3-5个
            2. 实体是指查询中提到的具体事物、产品、人物等
            3. requiresContext表示是否需要上下文理解
            4. rewrittenQuery是对原查询的优化表述,使其更适合检索
            """;

    private static final String PREFERENCE_ENHANCED_PROMPT = """
            你是一个智能查询分析助手。请分析用户的查询并返回结构化的JSON结果。

            用户查询: {query}

            用户偏好信息:
            - 偏好品牌: {preferredBrands}
            - 偏好类目: {preferredCategories}
            - 价格范围: {priceRange}
            - 近期搜索: {recentSearches}
            - 关键词偏好: {keywordWeights}

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["关键词1", "关键词2", "关键词3"],
                "entities": ["实体1", "实体2"],
                "requiresContext": false,
                "rewrittenQuery": "优化后的查询语句"
            }

            注意事项:
            1. 关键词数量控制在3-5个, 优先考虑用户偏好的关键词
            2. 实体是指查询中提到的具体事物、产品、人物等
            3. requiresContext表示是否需要上下文理解
            4. rewrittenQuery是对原查询的优化表述, 结合用户偏好使其更适合检索
            """;

    private static final String CONTEXT_ENHANCED_PROMPT = """
            你是一个智能查询分析助手。请分析用户的查询并返回结构化的JSON结果。

            对话历史:
            {conversationHistory}

            用户查询: {query}

            用户偏好信息:
            - 偏好品牌: {preferredBrands}
            - 偏好类目: {preferredCategories}
            - 价格范围: {priceRange}
            - 近期搜索: {recentSearches}
            - 关键词偏好: {keywordWeights}

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["关键词1", "关键词2", "关键词3"],
                "entities": ["实体1", "实体2"],
                "requiresContext": true,
                "rewrittenQuery": "优化后的查询语句(考虑对话上下文)"
            }

            注意事项:
            1. 关键词数量控制在3-5个, 优先考虑用户偏好和对话上下文中的关键词
            2. 实体是指查询中提到的具体事物、产品、人物等
            3. requiresContext应设置为true
            4. rewrittenQuery需要结合对话上下文进行优化, 如果用户使用代词(如"它"、"这个"、"那个"), 请解析为具体的实体
            """;

    /**
     * 构造函数
     * @param aiService AI服务
     */
    public QueryParserService(AIService aiService) {
        this.aiService = aiService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 解析用户查询
     * @param query 用户查询
     * @return 查询分析结果
     */
    public QueryAnalysis parse(String query) {
        log.info("Parsing query: {}", query);

        String prompt = ANALYSIS_PROMPT.replace("{query}", query);
        String responseContent = aiService.generate(prompt);

        if (responseContent == null) {
            throw new RuntimeException("AI service returned empty response for query parsing: " + query);
        }

        log.debug("Model response for query parsing: {}", responseContent);

        try {
            QueryAnalysis analysis = parseJsonResponse(responseContent);
            analysis.setOriginalQuery(query);
            return analysis;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse query parsing response: " + e.getMessage());
        }
    }

    /**
     * 解析用户查询（带用户偏好）
     * @param query 用户查询
     * @param preference 用户偏好
     * @return 查询分析结果
     */
    public QueryAnalysis parse(String query, UserPreference preference) {
        log.info("Parsing query with preference for user: {}", preference.getUserId());

        String prompt = buildPreferenceEnhancedPrompt(query, preference);
        String responseContent = aiService.generate(prompt);

        if (responseContent == null) {
            throw new RuntimeException("AI service returned empty response for query parsing: " + query);
        }

        log.debug("Model response for query parsing with preference: {}", responseContent);

        try {
            QueryAnalysis analysis = parseJsonResponse(responseContent);
            analysis.setOriginalQuery(query);
            return analysis;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse query parsing response: " + e.getMessage());
        }
    }

    /**
     * 解析用户查询（带用户偏好和对话上下文）
     * @param query 用户查询
     * @param preference 用户偏好
     * @param conversationContext 对话上下文
     * @return 查询分析结果
     */
    public QueryAnalysis parse(String query, UserPreference preference, List<MemoryItem> conversationContext) {
        log.info("Parsing query with preference and context for user: {}", preference.getUserId());

        String prompt = buildContextEnhancedPrompt(query, preference, conversationContext);
        String responseContent = aiService.generate(prompt);

        if (responseContent == null) {
            throw new RuntimeException("AI service returned empty response for query parsing: " + query);
        }

        log.debug("Model response for query parsing with context: {}", responseContent);

        try {
            QueryAnalysis analysis = parseJsonResponse(responseContent);
            analysis.setOriginalQuery(query);
            return analysis;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse query parsing response: " + e.getMessage());
        }
    }

    /**
     * 解析JSON响应
     * @param json JSON字符串
     * @return 查询分析结果
     * @throws JsonProcessingException JSON解析异常
     */
    private QueryAnalysis parseJsonResponse(String json) throws JsonProcessingException {
        String cleanJson = JsonUtils.stripMarkdownCodeBlock(json);
        Map<String, Object> map = objectMapper.readValue(cleanJson, Map.class);

        QueryAnalysis analysis = new QueryAnalysis();
        analysis.setOriginalQuery("");

        if (map.containsKey("intentType")) {
            analysis.setIntentType(String.valueOf(map.get("intentType")));
        }

        if (map.containsKey("keywords")) {
            List<String> keywords = new ArrayList<>();
            Object keywordsObj = map.get("keywords");
            if (keywordsObj instanceof List) {
                for (Object item : (List<?>) keywordsObj) {
                    keywords.add(String.valueOf(item));
                }
            }
            analysis.setKeywords(keywords);
        }

        if (map.containsKey("entities")) {
            List<String> entities = new ArrayList<>();
            Object entitiesObj = map.get("entities");
            if (entitiesObj instanceof List) {
                for (Object item : (List<?>) entitiesObj) {
                    entities.add(String.valueOf(item));
                }
            }
            analysis.setEntities(entities);
        }

        if (map.containsKey("requiresContext")) {
            analysis.setRequiresContext((Boolean) map.get("requiresContext"));
        }

        if (map.containsKey("rewrittenQuery")) {
            analysis.setRewrittenQuery(String.valueOf(map.get("rewrittenQuery")));
        }

        return analysis;
    }

    private String buildPreferenceEnhancedPrompt(String query, UserPreference preference) {
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

        String recentSearches = preference.getRecentSearchedKeywords().isEmpty()
                ? "无"
                : String.join(", ", preference.getRecentSearchedKeywords());

        String keywordWeights = preference.getKeywordWeights().isEmpty()
                ? "无"
                : preference.getKeywordWeights().entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(5)
                        .map(e -> e.getKey() + "(" + String.format("%.2f", e.getValue()) + ")")
                        .collect(Collectors.joining(", "));

        return PREFERENCE_ENHANCED_PROMPT
                .replace("{query}", query)
                .replace("{preferredBrands}", preferredBrands)
                .replace("{preferredCategories}", preferredCategories)
                .replace("{priceRange}", priceRange)
                .replace("{recentSearches}", recentSearches)
                .replace("{keywordWeights}", keywordWeights);
    }

    /**
     * 构建带对话上下文的提示词
     * @param query 用户查询
     * @param preference 用户偏好
     * @param conversationContext 对话上下文
     * @return 提示词字符串
     */
    private String buildContextEnhancedPrompt(String query, UserPreference preference, List<MemoryItem> conversationContext) {
        // 构建对话历史字符串
        StringBuilder historyBuilder = new StringBuilder();
        for (MemoryItem item : conversationContext) {
            String role = "USER".equals(item.getRole()) ? "用户" : "助手";
            historyBuilder.append(role).append(": ").append(item.getContent()).append("\n");
        }
        String conversationHistory = historyBuilder.toString();

        // 构建偏好信息（复用已有方法）
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

        String recentSearches = preference.getRecentSearchedKeywords().isEmpty()
                ? "无"
                : String.join(", ", preference.getRecentSearchedKeywords());

        String keywordWeights = preference.getKeywordWeights().isEmpty()
                ? "无"
                : preference.getKeywordWeights().entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(5)
                        .map(e -> e.getKey() + "(" + String.format("%.2f", e.getValue()) + ")")
                        .collect(Collectors.joining(", "));

        return CONTEXT_ENHANCED_PROMPT
                .replace("{conversationHistory}", conversationHistory)
                .replace("{query}", query)
                .replace("{preferredBrands}", preferredBrands)
                .replace("{preferredCategories}", preferredCategories)
                .replace("{priceRange}", priceRange)
                .replace("{recentSearches}", recentSearches)
                .replace("{keywordWeights}", keywordWeights);
    }
}