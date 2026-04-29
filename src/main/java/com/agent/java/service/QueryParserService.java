package com.agent.java.service;

import com.agent.java.model.search.QueryAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            return parseJsonResponse(responseContent);
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
        Map<String, Object> map = objectMapper.readValue(json, Map.class);

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
}