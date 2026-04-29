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
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.store.InMemoryStore;
import io.agentscope.core.rag.store.dto.SearchDocumentDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 向量召回服务
 * 使用 OllamaTextEmbedding 进行文本向量化，使用 InMemoryStore 存储和检索向量数据
 */
@Slf4j
@Service
public class VectorRecallService {

    private final OllamaTextEmbedding embeddingModel;
    private final InMemoryStore vectorStore;
    private final OllamaConfig ollamaConfig;

    /**
     * 构造函数，使用配置类初始化 OllamaTextEmbedding 和 InMemoryStore
     */
    public VectorRecallService(OllamaConfig ollamaConfig) {
        this.ollamaConfig = ollamaConfig;

        log.info("Initializing OllamaTextEmbedding with baseUrl={}, modelName={}, dimensions={}",
                ollamaConfig.getBaseUrl(), ollamaConfig.getModelName(), ollamaConfig.getDimensions());

        // 使用配置初始化 OllamaTextEmbedding
        this.embeddingModel = OllamaTextEmbedding.builder()
                .baseUrl(ollamaConfig.getBaseUrl())
                .modelName(ollamaConfig.getModelName())
                .dimensions(ollamaConfig.getDimensions())
                .build();

        // 使用配置的维度初始化 InMemoryStore
        this.vectorStore = InMemoryStore.builder()
                .dimensions(ollamaConfig.getDimensions())
                .build();

        log.info("VectorRecallService initialized successfully");
    }

    @PostConstruct
    public void init() {
        loadGoodsData();
        log.info("VectorRecallService post-construct completed. Loaded {} documents.", vectorStore.size());
    }

    /**
     * 从 resources/goods.csv 文件加载商品数据
     */
    private void loadGoodsData() {
        try (InputStream is = getClass().getResourceAsStream("/goods.csv");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            if (is == null) {
                log.warn("goods.csv not found in resources");
                return;
            }

            String line;
            boolean isFirstLine = true;
            List<io.agentscope.core.rag.model.Document> documents = new ArrayList<>();

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

                    // 构建文档内容
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
                    io.agentscope.core.rag.model.Document doc = new io.agentscope.core.rag.model.Document(metadata);
                    doc.setEmbedding(embedding);

                    documents.add(doc);
                    log.debug("Loaded goods: {}", title);
                }
            }

            // 批量添加到向量存储
            if (!documents.isEmpty()) {
                vectorStore.add(documents).block();
                log.info("Successfully loaded {} goods documents", documents.size());
            }

        } catch (Exception e) {
            log.error("Failed to load goods data", e);
        }
    }

    /**
     * 根据查询分析结果进行向量召回
     *
     * @param analysis 查询分析结果
     * @param topK     返回数量
     * @return 召回的文档列表
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
        List<io.agentscope.core.rag.model.Document> results = vectorStore.search(searchDto).block();

        // 转换为 SearchDocument 对象
        List<SearchDocument> documents = new ArrayList<>();
        if (results != null) {
            for (io.agentscope.core.rag.model.Document result : results) {
                SearchDocument doc = new SearchDocument();
                doc.setId(result.getId());
                doc.setTitle(result.getMetadata().getContentText());
                doc.setContent(result.getMetadata().getContentText());
                doc.setVectorScore(result.getScore() != null ? result.getScore() : 0.0);
                documents.add(doc);
            }
        }

        log.debug("Recalled {} documents for query: {}", documents.size(), query);
        return documents;
    }

    /**
     * 重新加载商品数据
     */
    public void reloadKnowledgeBase() {
        vectorStore.clear();
        loadGoodsData();
    }

    /**
     * 获取文档数量
     */
    public int getDocumentCount() {
        return vectorStore.size();
    }
}