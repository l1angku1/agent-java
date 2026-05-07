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
        if (request.isEnableRerank() && !recalledDocs.isEmpty()) {
            long rerankStartTime = System.currentTimeMillis();
            recalledDocs = useMemory
                    ? modelRerankService.rerank(request.getQuery(), recalledDocs, preference)
                    : modelRerankService.rerank(request.getQuery(), recalledDocs);
            rerankTime = System.currentTimeMillis() - rerankStartTime;
            log.debug("Step 3 - Model rerank completed in {}ms", rerankTime);
        }

        // 步骤4: 大模型评估
        EvaluationResult evaluation = null;
        if (request.isEnableEvaluation()) {
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

    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestParam(value = "query", defaultValue = "智能台灯有哪些功能") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK,
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "sessionId", required = false) String sessionId) {

        log.info("AI Ask request: {}", query);

        // 检查语义缓存
        String cachedAnswer = semanticCacheService.get(query);
        if (cachedAnswer != null) {
            log.info("Semantic cache hit for query: {}", query);
                    return ResponseEntity.ok(Map.of(
                    "query", query,
                    "answer", cachedAnswer,
                    "goodsCount", 0,
                    "qualityLevel", "cached",
                    "f1Score", 0.0,
                    "totalTime", 0,
                    "personalized", userId != null,
                    "cacheHit", true));
        }

        SearchRequest request = new SearchRequest(query);
        request.setTopK(topK);
        request.setEnableRerank(true);
        request.setEnableEvaluation(true);
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setEnableMemory(userId != null);

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
        return ResponseEntity.ok(Map.of(
                "query", query, "answer", answer, "goodsCount",
                searchResult != null ? searchResult.getDocuments().size() : 0,
                "qualityLevel",
                searchResult != null && searchResult.getEvaluation() != null
                        ? searchResult.getEvaluation().getQualityLevel()
                        : "未知",
                "f1Score",
                searchResult != null && searchResult.getEvaluation() != null ? searchResult.getEvaluation().getF1Score()
                        : 0.0,
                "totalTime", searchResult != null ? searchResult.getTotalTime() : 0, "personalized", userId != null,
                "cacheHit", false));
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

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP", "service", "AI Search Service", "goodsCount",
                String.valueOf(vectorRecallService.getGoodsCount())));
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