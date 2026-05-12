package com.agent.java.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.QueryAnalysis;
import com.agent.java.util.JsonUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 查询解析服务
 * 使用大模型分析用户查询，提取意图、关键词、实体等信息
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryParserService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;

    private static final String ANALYSIS_PROMPT = """
            你是一个智能查询分析助手。请分析用户的查询并返回结构化的JSON结果。

            用户查询: {query}

            商品实体结构定义:
            - id: 商品ID (字符串)
            - title: 商品标题 (字符串)
            - price: 价格 (数字, 单位: 元)
            - stock: 库存数量 (整数)
            - salesVolume: 销量 (整数)
            - shareCount: 转发量 (整数)
            - brand: 品牌 (字符串)
            - category: 类目 (字符串)

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["名词1", "名词2"],
                "entities": ["实体1", "实体2"],
                "requiresContext": false,
                "rewrittenQuery": "优化后的查询语句(只包含名词, 不含动词和过滤条件)",
                "filterExpression": "过滤条件表达式或null"
            }

            注意事项:
            1. entities(实体): 只提取查询中提到的具体事物、产品名称、类别名称等名词
            2. keywords(关键词): 提取用于语义匹配的重要词汇, 只包含名词和形容词, 不包含动词和数值条件
            3. rewrittenQuery: 优化后的查询语句, 只保留名词性词汇, 不含"采购"、"购买"、"需要"等动词, 不含价格、库存等条件
            4. filterExpression: 根据查询中的数值条件生成过滤表达式, 规则如下:
               - 使用 && 表示逻辑与, || 表示逻辑或
               - 支持的比较运算符: ==, !=, <, <=, >, >=
               - 使用商品实体字段名: price, stock, salesVolume, shareCount
               - 没有过滤条件时返回 null
               - 示例1: "price < 1000"
               - 示例2: "price >= 500 && price < 1000 && stock > 0"
               - 示例3: "salesVolume > 1000 || shareCount > 500"
            5. 动词(如"采购"、"购买"、"需要"、"推荐")不应出现在entities、keywords和rewrittenQuery中
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

            商品实体结构定义:
            - id: 商品ID (字符串)
            - title: 商品标题 (字符串)
            - price: 价格 (数字, 单位: 元)
            - stock: 库存数量 (整数)
            - salesVolume: 销量 (整数)
            - shareCount: 转发量 (整数)
            - brand: 品牌 (字符串)
            - category: 类目 (字符串)

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["名词1", "名词2"],
                "entities": ["实体1", "实体2"],
                "requiresContext": false,
                "rewrittenQuery": "优化后的查询语句(只包含名词, 不含动词和过滤条件)",
                "filterExpression": "过滤条件表达式或null"
            }

            注意事项:
            1. entities(实体): 只提取查询中提到的具体事物、产品名称、类别名称等名词, 优先考虑用户偏好的类目和品牌
            2. keywords(关键词): 提取用于语义匹配的重要词汇, 只包含名词和形容词, 优先考虑用户偏好的关键词, 不包含动词和数值条件
            3. rewrittenQuery: 优化后的查询语句, 只保留名词性词汇, 不含"采购"、"购买"、"需要"等动词, 不含价格、库存等条件, 结合用户偏好
            4. filterExpression: 根据查询中的数值条件生成过滤表达式, 规则如下:
               - 使用 && 表示逻辑与, || 表示逻辑或
               - 支持的比较运算符: ==, !=, <, <=, >, >=
               - 使用商品实体字段名: price, stock, salesVolume, shareCount
               - 没有过滤条件时返回 null
               - 示例: "price >= 500 && price < 1000 && stock > 0"
            5. 动词不应出现在entities、keywords和rewrittenQuery中
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

            商品实体结构定义:
            - id: 商品ID (字符串)
            - title: 商品标题 (字符串)
            - price: 价格 (数字, 单位: 元)
            - stock: 库存数量 (整数)
            - salesVolume: 销量 (整数)
            - shareCount: 转发量 (整数)
            - brand: 品牌 (字符串)
            - category: 类目 (字符串)

            请按照以下JSON格式输出:
            {
                "intentType": "意图类型(可选值: 问答、事实查询、对比、建议、其他)",
                "keywords": ["名词1", "名词2"],
                "entities": ["实体1", "实体2"],
                "requiresContext": true,
                "rewrittenQuery": "优化后的查询语句(只包含名词, 不含动词和过滤条件, 考虑对话上下文)",
                "filterExpression": "过滤条件表达式或null"
            }

            注意事项:
            1. entities(实体): 只提取查询中提到的具体事物、产品名称、类别名称等名词, 优先考虑用户偏好和对话上下文中的实体
            2. keywords(关键词): 提取用于语义匹配的重要词汇, 只包含名词和形容词, 优先考虑用户偏好和对话上下文中的关键词, 不包含动词和数值条件
            3. rewrittenQuery: 优化后的查询语句, 只保留名词性词汇, 不含动词, 不含价格、库存等条件, 结合对话上下文和用户偏好, 如果用户使用代词(如"它"、"这个"、"那个"), 请解析为具体的实体
            4. filterExpression: 根据查询中的数值条件生成过滤表达式, 规则如下:
               - 使用 && 表示逻辑与, || 表示逻辑或
               - 支持的比较运算符: ==, !=, <, <=, >, >=
               - 使用商品实体字段名: price, stock, salesVolume, shareCount
               - 没有过滤条件时返回 null
            5. 动词不应出现在entities、keywords和rewrittenQuery中
            """;

    /**
     * 解析用户查询
     * 
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
     * 
     * @param query      用户查询
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
     * 
     * @param query               用户查询
     * @param preference          用户偏好
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
     * 
     * @param json JSON字符串
     * @return 查询分析结果
     * @throws JsonProcessingException JSON解析异常
     */
    private QueryAnalysis parseJsonResponse(String json) throws JsonProcessingException {
        String cleanJson = JsonUtils.stripMarkdownCodeBlock(json);
        Map<String, Object> map = objectMapper.readValue(cleanJson, new TypeReference<Map<String, Object>>() {
        });

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

        // 解析过滤条件表达式
        if (map.containsKey("filterExpression")) {
            Object filterExprObj = map.get("filterExpression");
            if (filterExprObj != null && !filterExprObj.toString().equalsIgnoreCase("null")) {
                analysis.setFilterExpression(String.valueOf(filterExprObj));
            }
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
     * 
     * @param query               用户查询
     * @param preference          用户偏好
     * @param conversationContext 对话上下文
     * @return 提示词字符串
     */
    private String buildContextEnhancedPrompt(String query, UserPreference preference,
            List<MemoryItem> conversationContext) {
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