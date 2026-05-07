package com.agent.java.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.EvaluationResult;
import com.agent.java.model.search.SearchDocument;

import lombok.extern.slf4j.Slf4j;

/**
 * 响应生成服务
 * 使用大模型基于检索到的商品生成最终回答
 */
@Slf4j
@Service
public class ResponseGeneratorService {

    private final AIService aiService;

    private static final String RESPONSE_PROMPT = "你是一个专业的商品推荐助手。请根据以下检索到的商品信息，用自然、友好的语言回答用户的问题。\n\n" +
            "检索到的商品:\n" +
            "---\n" +
            "%s\n" +
            "---\n\n" +
            "用户问题: %s\n\n" +
            "请基于以上商品信息回答问题, 如果信息足够, 请直接给出具体的商品推荐和理由; 如果信息不足, 请用中文说明无法回答的原因。";

    private static final String PREFERENCE_RESPONSE_PROMPT = "你是一个专业的商品推荐助手。请根据以下检索到的商品信息和用户偏好，用符合用户沟通风格的语言回答用户的问题。\n\n"
            +
            "用户偏好:\n" +
            "- 偏好品牌: %s\n" +
            "- 偏好类目: %s\n" +
            "- 价格范围: %s\n" +
            "- 沟通风格: %s\n\n" +
            "检索到的商品:\n" +
            "---\n" +
            "%s\n" +
            "---\n\n" +
            "用户问题: %s\n\n" +
            "请基于以上商品信息和用户偏好回答问题，优先推荐符合用户偏好的商品。如果信息足够, 请直接给出具体的商品推荐和理由; 如果信息不足, 请用中文说明无法回答的原因。";

    /**
     * 构造函数
     * 
     * @param aiService AI服务
     */
    public ResponseGeneratorService(AIService aiService) {
        this.aiService = aiService;
    }

    /**
     * 生成最终响应（三参数版本）
     *
     * @param query      用户查询
     * @param documents  检索到的商品列表
     * @param evaluation 评估结果
     * @return 生成的回答
     */
    public String generate(String query, List<SearchDocument> documents, EvaluationResult evaluation) {
        return generate(query, documents);
    }

    /**
     * 生成最终响应
     *
     * @param query     用户查询
     * @param documents 检索到的商品列表
     * @return 生成的回答
     */
    public String generate(String query, List<SearchDocument> documents) {
        log.info("Generating response for query: {}", query);

        if (documents == null || documents.isEmpty()) {
            return "未找到相关商品信息，无法回答您的问题。";
        }

        String goodsInfo = formatGoodsInfo(documents);
        String prompt = String.format(RESPONSE_PROMPT, goodsInfo, query);
        return callAIService(prompt, query);
    }

    /**
     * 生成最终响应（带用户偏好）
     *
     * @param query      用户查询
     * @param documents  检索到的商品列表
     * @param preference 用户偏好
     * @return 生成的回答
     */
    public String generate(String query, List<SearchDocument> documents, UserPreference preference) {
        log.info("Generating personalized response for user: {}", preference.getUserId());

        if (documents == null || documents.isEmpty()) {
            return "未找到相关商品信息，无法回答您的问题。";
        }

        String goodsInfo = formatGoodsInfo(documents);
        String prompt = buildPreferencePrompt(query, goodsInfo, preference);
        return callAIService(prompt, query);
    }

    /**
     * 构建带偏好的提示词
     */
    private String buildPreferencePrompt(String query, String goodsInfo, UserPreference preference) {
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

        String communicationStyle = preference.getCommunicationStyle() != null
                ? preference.getCommunicationStyle()
                : "友好";

        return String.format(PREFERENCE_RESPONSE_PROMPT,
                preferredBrands, preferredCategories, priceRange, communicationStyle,
                goodsInfo, query);
    }

    /**
     * 调用AI服务
     */
    private String callAIService(String prompt, String query) {
        String response = aiService.generate(prompt);

        if (response == null || response.isEmpty()) {
            throw new RuntimeException("AI service returned empty response for query: " + query);
        }

        log.debug("Generated response (length: {})", response.length());
        return response;
    }

    /**
     * 格式化商品信息
     */
    private String formatGoodsInfo(List<SearchDocument> documents) {
        StringBuilder goodsInfo = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            SearchDocument doc = documents.get(i);
            goodsInfo.append(String.format("%d. %s\n   价格: %s\n   描述: %s\n\n",
                    i + 1,
                    doc.getTitle(),
                    extractPrice(doc.getContent()),
                    extractDescription(doc.getContent())));
        }
        return goodsInfo.toString();
    }

    /**
     * 从内容中提取价格信息
     * 
     * @param content 商品内容
     * @return 价格字符串
     */
    private String extractPrice(String content) {
        if (content == null)
            return "未知";

        java.util.regex.Pattern pricePattern = java.util.regex.Pattern.compile("价格: ([\\d.]+)");
        java.util.regex.Matcher matcher = pricePattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "未知";
    }

    /**
     * 从内容中提取描述信息
     * 
     * @param content 商品内容
     * @return 描述字符串
     */
    private String extractDescription(String content) {
        if (content == null)
            return "暂无描述";

        java.util.regex.Pattern descPattern = java.util.regex.Pattern.compile("描述: ([^\n]+)");
        java.util.regex.Matcher matcher = descPattern.matcher(content);
        if (matcher.find()) {
            String desc = matcher.group(1);
            return desc.length() > 50 ? desc.substring(0, 50) + "..." : desc;
        }
        return "暂无描述";
    }
}