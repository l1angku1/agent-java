package com.agent.java.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.EvaluationResult;
import com.agent.java.model.search.QueryAnalysis;
import com.agent.java.model.search.SearchDocument;
import com.agent.java.model.search.SearchRequest;
import com.agent.java.model.search.SearchResult;
import com.agent.java.service.MemoryService;
import com.agent.java.service.ModelEvaluationService;
import com.agent.java.service.ModelRerankService;
import com.agent.java.service.QueryParserService;
import com.agent.java.service.ResponseGeneratorService;
import com.agent.java.service.SemanticCacheService;
import com.agent.java.service.VectorRecallService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI搜索接口
 * 提供AI搜索的创建、执行、查询等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-search")
@RequiredArgsConstructor
public class AISearchController {

    private final QueryParserService queryParserService;
    private final VectorRecallService vectorRecallService;
    private final ModelRerankService modelRerankService;
    private final ModelEvaluationService modelEvaluationService;
    private final ResponseGeneratorService responseGeneratorService;
    private final MemoryService memoryService;
    private final SemanticCacheService semanticCacheService;

    @PostMapping("/query")
    public ResponseEntity<SearchResult> search(@RequestBody SearchRequest request) {
        log.info("AI Search request received: {}", request.getQuery());

        long startTime = System.currentTimeMillis();
        boolean useMemory = request.isEnableMemory() && request.getUserId() != null;
        boolean useContext = useMemory && request.getSessionId() != null;
        UserPreference preference = null;
        List<MemoryItem> conversationContext = new ArrayList<>();

        if (useMemory) {
            preference = memoryService.getUserPreference(request.getUserId());
            log.info("Using personalized search for user: {}", request.getUserId());

            // 获取对话上下文（如果有会话ID）
            if (useContext) {
                conversationContext = memoryService.getConversationContext(request.getSessionId(), 5);
                log.info("Using conversation context with {} messages", conversationContext.size());
            }
        }

        // 步骤1: 解析用户输入 - 使用大模型进行意图识别
        long parseStartTime = System.currentTimeMillis();
        QueryAnalysis analysis = parseQueryWithContext(request.getQuery(), preference, conversationContext, useContext,
                useMemory);
        long parseTime = System.currentTimeMillis() - parseStartTime;
        log.debug("Step 1 - Query parsing completed in {}ms", parseTime);

        // 步骤2: 向量召回 - 使用大模型向量化（召回阶段不加入用户偏好，保持快速）
        long recallStartTime = System.currentTimeMillis();
        List<SearchDocument> recalledDocs = vectorRecallService.recall(analysis, request.getTopK());
        long recallTime = System.currentTimeMillis() - recallStartTime;
        log.debug("Step 2 - Vector recall completed in {}ms, found {} goods", recallTime, recalledDocs.size());

        // 步骤3: 大模型重排
        long rerankTime = 0;
        if (Boolean.TRUE.equals(request.getEnableRerank()) && !recalledDocs.isEmpty()) {
            long rerankStartTime = System.currentTimeMillis();
            recalledDocs = useMemory
                    ? modelRerankService.rerank(request.getQuery(), recalledDocs, preference)
                    : modelRerankService.rerank(request.getQuery(), recalledDocs);
            rerankTime = System.currentTimeMillis() - rerankStartTime;
            log.debug("Step 3 - Model rerank completed in {}ms", rerankTime);
        }

        // 步骤4: 大模型评估
        EvaluationResult evaluation = null;
        if (Boolean.TRUE.equals(request.getEnableEvaluation())) {
            long evalStartTime = System.currentTimeMillis();
            evaluation = modelEvaluationService.evaluate(request.getQuery(), recalledDocs);
            long evalTime = System.currentTimeMillis() - evalStartTime;
            log.debug("Step 4 - Model evaluation completed in {}ms - Quality: {}",
                    evalTime, evaluation != null ? evaluation.getQualityLevel() : "N/A");
        }

        // 步骤5: 记录搜索记忆和对话
        if (useMemory) {
            memoryService.recordSearch(request.getUserId(), request.getSessionId(), request.getQuery(), analysis);

            // 记录对话到会话上下文
            if (useContext) {
                String responseSummary = "搜索完成，找到 " + recalledDocs.size() + " 条相关商品";
                memoryService.addConversation(request.getSessionId(), request.getUserId(), request.getQuery(),
                        responseSummary);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;

        SearchResult result = new SearchResult();
        result.setQuery(request.getQuery());
        result.setDocuments(recalledDocs);
        result.setEvaluation(evaluation);
        result.setRecallTime(recallTime);
        result.setRerankTime(rerankTime);
        result.setTotalTime(totalTime);

        log.info("AI Search completed in {}ms - recall: {}ms, rerank: {}ms, goods: {}",
                totalTime, recallTime, rerankTime, recalledDocs.size());

        return ResponseEntity.ok(result);
    }

    @PostMapping("/ask")
    public ResponseEntity<SearchResult> ask(@RequestBody SearchRequest request) {
        String query = request.getQuery();
        String userId = request.getUserId();

        if (query == null || query.trim().isEmpty()) {
            query = "智能台灯";
        }

        log.info("AI Ask request: {}", query);

        // 检查语义缓存
        String cachedAnswer = semanticCacheService.get(query);
        if (cachedAnswer != null) {
            log.info("Semantic cache hit for query: {}", query);
            SearchResult cachedResult = new SearchResult();
            cachedResult.setQuery(query);
            cachedResult.setAnswer(cachedAnswer);
            cachedResult.setGoodsCount(0);
            cachedResult.setQualityLevel("cached");
            cachedResult.setF1Score(0.0);
            cachedResult.setTotalTime(0);
            cachedResult.setPersonalized(userId != null);
            cachedResult.setCacheHit(true);
            return ResponseEntity.ok(cachedResult);
        }

        if (request.getEnableRerank() == null) {
            request.setEnableRerank(true);
        }
        if (request.getEnableEvaluation() == null) {
            request.setEnableEvaluation(true);
        }
        if (userId != null) {
            request.setEnableMemory(true);
        }

        SearchResult searchResult = search(request).getBody();

        // 步骤6: 响应生成 - 使用大模型生成最终回答
        String answer;
        if (userId != null && searchResult != null) {
            UserPreference preference = memoryService.getUserPreference(userId);
            answer = responseGeneratorService.generate(query, searchResult.getDocuments(), preference);
        } else {
            answer = responseGeneratorService.generate(query,
                    searchResult != null ? searchResult.getDocuments() : List.of(),
                    searchResult != null ? searchResult.getEvaluation() : null);
        }

        // 存储到语义缓存
        semanticCacheService.put(query, answer);

        // 构建返回结果
        SearchResult result = new SearchResult();
        result.setQuery(query);
        result.setAnswer(answer);
        if (searchResult != null) {
            result.setDocuments(searchResult.getDocuments());
            result.setGoodsCount(searchResult.getDocuments().size());
            result.setEvaluation(searchResult.getEvaluation());
            result.setRecallTime(searchResult.getRecallTime());
            result.setRerankTime(searchResult.getRerankTime());
            result.setTotalTime(searchResult.getTotalTime());
            if (searchResult.getEvaluation() != null) {
                result.setQualityLevel(searchResult.getEvaluation().getQualityLevel());
                result.setF1Score(searchResult.getEvaluation().getF1Score());
            } else {
                result.setQualityLevel("未知");
                result.setF1Score(0.0);
            }
        } else {
            result.setGoodsCount(0);
            result.setQualityLevel("未知");
            result.setF1Score(0.0);
        }
        result.setPersonalized(userId != null);
        result.setCacheHit(false);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/analyze")
    public ResponseEntity<QueryAnalysis> analyzeQuery(@RequestParam("query") String query) {
        QueryAnalysis analysis = queryParserService.parse(query);
        analysis.setOriginalQuery(query);
        return ResponseEntity.ok(analysis);
    }

    private QueryAnalysis parseQueryWithContext(String query, UserPreference preference,
            List<MemoryItem> conversationContext, boolean useContext, boolean useMemory) {
        if (useContext && !conversationContext.isEmpty()) {
            return queryParserService.parse(query, preference, conversationContext);
        } else if (useMemory) {
            return queryParserService.parse(query, preference);
        } else {
            return queryParserService.parse(query);
        }
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadGoods() {
        vectorRecallService.reloadGoods();
        return ResponseEntity.ok(Map.of(
                "status", "success", "goodsCount", String.valueOf(vectorRecallService.getGoodsCount()),
                "message",
                "Goods data reloaded successfully"));
    }
}