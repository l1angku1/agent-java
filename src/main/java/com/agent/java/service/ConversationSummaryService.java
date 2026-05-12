package com.agent.java.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.agent.java.model.memory.MemoryItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 对话总结服务
 * <p>
 * 提供对话历史总结和关键词提取功能，使用LLM进行智能总结。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final AIService aiService;

    /**
     * 总结提示词模板
     */
    private static final String SUMMARY_PROMPT = """
            请总结以下对话的要点：

            %s

            请用简洁的语言总结对话中的关键信息、用户需求和偏好。
            """;

    /**
     * 关键词提取提示词模板
     */
    private static final String KEYWORD_PROMPT = """
            请从以下文本中提取重要的关键词和用户偏好，用逗号分隔：

            %s
            """;

    /**
     * 总结对话历史
     * <p>
     * 使用LLM对对话历史进行总结，提取关键信息、用户需求和偏好。
     * </p>
     *
     * @param conversationHistory 对话历史列表
     * @return 总结文本
     */
    public String summarize(List<MemoryItem> conversationHistory) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return "";
        }

        try {
            // 构建对话历史字符串
            StringBuilder historyBuilder = new StringBuilder();
            for (MemoryItem item : conversationHistory) {
                String role = "USER".equals(item.getRole()) ? "用户" : "助手";
                historyBuilder.append(role).append(": ").append(item.getContent()).append("\n");
            }

            // 构建提示词
            String prompt = String.format(SUMMARY_PROMPT, historyBuilder.toString());

            // 调用LLM
            String result = aiService.generate(prompt);

            log.debug("Generated conversation summary: {}", result);
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("Failed to summarize conversation", e);
            return "";
        }
    }

    /**
     * 从文本中提取关键词
     * <p>
     * 使用LLM从文本中提取重要的关键词和用户偏好。
     * </p>
     *
     * @param text 输入文本
     * @return 关键词列表
     */
    public List<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String prompt = String.format(KEYWORD_PROMPT, text);
            String result = aiService.generate(prompt);

            if (result != null && !result.isEmpty()) {
                return Arrays.stream(result.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && s.length() > 1)
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to extract keywords", e);
            return Collections.emptyList();
        }
    }
}
