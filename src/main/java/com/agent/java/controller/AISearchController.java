package com.agent.java.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.search.EvaluationResult;
import com.agent.java.model.search.QueryAnalysis;
import com.agent.java.model.search.SearchDocument;
import com.agent.java.model.search.SearchRequest;
import com.agent.java.model.search.SearchResult;
import com.agent.java.service.ModelEvaluationService;
import com.agent.java.service.ModelRerankService;
import com.agent.java.service.QueryParserService;
import com.agent.java.service.ResponseGeneratorService;
import com.agent.java.service.VectorRecallService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/ai-search")
public class AISearchController {

    private final QueryParserService queryParserService;
    private final VectorRecallService vectorRecallService;
    private final ModelRerankService modelRerankService;
    private final ModelEvaluationService modelEvaluationService;
    private final ResponseGeneratorService responseGeneratorService;

    public AISearchController(QueryParserService queryParserService,
            VectorRecallService vectorRecallService,
            ModelRerankService modelRerankService,
            ModelEvaluationService modelEvaluationService,
            ResponseGeneratorService responseGeneratorService) {
        this.queryParserService = queryParserService;
        this.vectorRecallService = vectorRecallService;
        this.modelRerankService = modelRerankService;
        this.modelEvaluationService = modelEvaluationService;
        this.responseGeneratorService = responseGeneratorService;
    }

    @PostMapping("/query")
    public ResponseEntity<SearchResult> search(@RequestBody SearchRequest request) {
        log.info("AI Search request received: {}", request.getQuery());

        long startTime = System.currentTimeMillis();

        // 步骤1: 解析用户输入 - 使用大模型进行意图识别
        long parseStartTime = System.currentTimeMillis();
        QueryAnalysis analysis = queryParserService.parse(request.getQuery());
        long parseTime = System.currentTimeMillis() - parseStartTime;
        log.debug("Step 1 - Query parsing completed in {}ms", parseTime);

        // 步骤2: 向量召回 - 使用大模型向量化
        long recallStartTime = System.currentTimeMillis();
        List<SearchDocument> recalledDocs = vectorRecallService.recall(analysis, request.getTopK());
        long recallTime = System.currentTimeMillis() - recallStartTime;
        log.debug("Step 2 - Vector recall completed in {}ms, found {} documents", recallTime, recalledDocs.size());

        // 步骤3: 大模型重排
        long rerankTime = 0;
        if (request.isEnableRerank() && !recalledDocs.isEmpty()) {
            long rerankStartTime = System.currentTimeMillis();
            recalledDocs = modelRerankService.rerank(request.getQuery(), recalledDocs);
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

        long totalTime = System.currentTimeMillis() - startTime;

        SearchResult result = new SearchResult();
        result.setQuery(request.getQuery());
        result.setDocuments(recalledDocs);
        result.setEvaluation(evaluation);
        result.setRecallTime(recallTime);
        result.setRerankTime(rerankTime);
        result.setTotalTime(totalTime);

        log.info("AI Search completed in {}ms - recall: {}ms, rerank: {}ms, docs: {}",
                totalTime, recallTime, rerankTime, recalledDocs.size());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(
            @RequestParam(value = "query", defaultValue = "智能台灯有哪些功能") String query,
            @RequestParam(value = "topK", defaultValue = "5") int topK) {

        log.info("AI Ask request: {}", query);

        SearchRequest request = new SearchRequest(query);
        request.setTopK(topK);
        request.setEnableRerank(true);
        request.setEnableEvaluation(true);

        SearchResult searchResult = search(request).getBody();

        // 步骤5: 响应生成 - 使用大模型生成最终回答
        String answer = responseGeneratorService.generate(query,
                searchResult != null ? searchResult.getDocuments() : List.of(),
                searchResult != null ? searchResult.getEvaluation() : null);

        return ResponseEntity.ok(Map.of(
                "query", query,
                "answer", answer,
                "documentCount", searchResult != null ? searchResult.getDocuments().size() : 0,
                "qualityLevel",
                searchResult != null && searchResult.getEvaluation() != null
                        ? searchResult.getEvaluation().getQualityLevel()
                        : "未知",
                "f1Score",
                searchResult != null && searchResult.getEvaluation() != null ? searchResult.getEvaluation().getF1Score()
                        : 0.0,
                "totalTime", searchResult != null ? searchResult.getTotalTime() : 0));
    }

    @GetMapping("/analyze")
    public ResponseEntity<QueryAnalysis> analyzeQuery(@RequestParam("query") String query) {
        QueryAnalysis analysis = queryParserService.parse(query);
        analysis.setOriginalQuery(query);
        return ResponseEntity.ok(analysis);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "AI Search Service",
                "documentCount", String.valueOf(vectorRecallService.getDocumentCount())));
    }

    @GetMapping("/process")
    public ResponseEntity<Map<String, Object>> getProcessInfo() {
        return ResponseEntity.ok(Map.of(
                "process", "AI Search Pipeline",
                "steps", List.of(
                        Map.of("step", 1, "name", "解析用户输入", "description", "使用大模型进行意图识别和关键词提取"),
                        Map.of("step", 2, "name", "向量召回", "description", "使用大模型向量化后进行相似度匹配"),
                        Map.of("step", 3, "name", "大模型重排", "description", "使用大模型对召回结果进行排序评分"),
                        Map.of("step", 4, "name", "大模型评估", "description", "使用大模型评估检索质量"),
                        Map.of("step", 5, "name", "响应生成", "description", "使用大模型生成最终回答")),
                "documentCount", vectorRecallService.getDocumentCount()));
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadKnowledgeBase() {
        vectorRecallService.reloadKnowledgeBase();
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "documentCount", String.valueOf(vectorRecallService.getDocumentCount()),
                "message", "Knowledge base reloaded successfully"));
    }
}