package com.agent.java.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.agent.java.config.OllamaConfig;
import com.agent.java.model.search.QueryAnalysis;
import com.agent.java.model.search.SearchDocument;

import io.agentscope.core.embedding.ollama.OllamaTextEmbedding;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 向量召回服务
 * 使用 OllamaTextEmbedding 进行文本向量化，使用 InMemoryStore 存储和检索向量数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorRecallService {

    private final OllamaConfig ollamaConfig;
    private OllamaTextEmbedding embeddingModel;
    private InMemoryStore vectorStore;

    @PostConstruct
    public void init() {
        log.info("Initializing OllamaTextEmbedding with baseUrl={}, modelName={}, dimensions={}",
                ollamaConfig.getBaseUrl(), ollamaConfig.getModelName(), ollamaConfig.getDimensions());

        // 初始化 OllamaTextEmbedding 模型
        embeddingModel = OllamaTextEmbedding.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(ollamaConfig.getModelName())
                .dimensions(ollamaConfig.getDimensions())
                .build();

        // 初始化 InMemoryStore
        vectorStore = InMemoryStore.builder()
                .dimensions(ollamaConfig.getDimensions())
                .build();

        log.info("VectorRecallService initialized successfully");

        // 加载商品数据
        loadGoods();
        log.info("VectorRecallService post-construct completed. Loaded {} goods.", vectorStore.size());
    }

    /**
     * 从 resources/goods.csv 文件加载商品数据
     */
    private void loadGoods() {
        try (InputStream is = getClass().getResourceAsStream("/goods.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            if (is == null) {
                log.warn("goods.csv not found in resources");
                return;
            }

            String line;
            boolean isFirstLine = true;
            List<Document> documents = new ArrayList<>();

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 跳过表头
                }

                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    String goodsId = parts[0].trim();
                    String title = parts[1].trim();
                    String description = parts[2].trim();
                    String brand = parts[3].trim();
                    String category = parts[4].trim();
                    String price = parts[5].trim();
                    // 构建商品内容
                    String content = String.format("商品ID: %s\n标题: %s\n描述: %s\n品牌: %s\n类目: %s\n价格: %s",
                            goodsId, title, description, brand, category, price);

                    // 使用 TextBlock 创建内容块，然后向量化
                    TextBlock textBlock = TextBlock.builder().text(content).build();

                    // 获取向量
                    double[] embedding = embeddingModel.embed(textBlock).block();

                    // 创建 DocumentMetadata
                    DocumentMetadata metadata = DocumentMetadata.builder()
                            .content(textBlock)
                            .docId(goodsId)
                            .chunkId(goodsId)
                            .build();

                    // 创建 Document
                    Document doc = new Document(metadata);
                    doc.setEmbedding(embedding);

                    documents.add(doc);
                    log.debug("Loaded goods: {}", title);
                }
            }

            // 批量添加到向量存储
            if (!documents.isEmpty()) {
                vectorStore.add(documents).block();
                log.info("Successfully loaded {} goods", documents.size());
            }

        } catch (Exception e) {
            log.error("Failed to load goods", e);
        }
    }

    /**
     * 根据查询分析结果进行向量召回
     *
     * @param analysis 查询分析结果
     * @param topK     返回数量
     * @return 召回的商品列表
     */
    public List<SearchDocument> recall(QueryAnalysis analysis, int topK) {
        String query = analysis.getRewrittenQuery() != null ? analysis.getRewrittenQuery()
                : analysis.getOriginalQuery();

        log.info("Recalling for query: {}", query);

        // 使用 TextBlock 创建内容块，然后向量化查询
        TextBlock queryTextBlock = TextBlock.builder().text(query).build();

        double[] queryEmbedding = embeddingModel.embed(queryTextBlock).block();

        // 构建搜索请求
        SearchDocumentDto searchDto = SearchDocumentDto.builder()
                .queryEmbedding(queryEmbedding)
                .limit(topK)
                .build();

        // 使用 InMemoryStore 进行向量检索
        List<Document> results = vectorStore.search(searchDto).block();

        // 转换为 SearchDocument 对象并收集所有分数
        List<SearchDocument> documents = new ArrayList<>();
        List<Double> allScores = new ArrayList<>();
        if (results != null) {
            for (Document result : results) {
                SearchDocument doc = new SearchDocument();
                doc.setId(result.getId());
                doc.setTitle(result.getMetadata().getContentText());
                doc.setContent(result.getMetadata().getContentText());
                double score = result.getScore() != null ? result.getScore() : 0.0;
                doc.setVectorScore(score);
                allScores.add(score);
                documents.add(doc);
            }
        }

        // 计算动态阈值并标记低分结果
        double threshold = calculateDynamicThreshold(allScores, 1.5);
        for (SearchDocument doc : documents) {
            doc.setLowQuality(doc.getVectorScore() < threshold);
        }

        log.debug("Recalled {} goods for query: {}, dynamic threshold: {}, low quality: {}",
                documents.stream().filter(SearchDocument::isLowQuality).count());

        return documents;
    }

    /**
     * 计算动态阈值：均值 - k * 标准差
     * 
     * @param scores 所有召回结果的分数列表
     * @param k      系数，通常取1.0~2.0
     * @return 动态阈值
     */
    private double calculateDynamicThreshold(List<Double> scores, double k) {
        if (scores == null || scores.isEmpty()) {
            return 0.4;
        }

        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = scores.stream()
                .mapToDouble(s -> Math.pow(s - mean, 2))
                .average().orElse(0.0);
        double stdDev = Math.sqrt(variance);

        double threshold = mean - k * stdDev;
        return Math.max(threshold, 0.3);
    }

    /**
     * 重新加载商品数据
     */
    public void reloadGoods() {
        vectorStore.clear();
        loadGoods();
    }

    /**
     * 获取商品数量
     */
    public int getGoodsCount() {
        return vectorStore.size();
    }
}