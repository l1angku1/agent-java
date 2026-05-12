package com.agent.java.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.agent.java.config.MemoryConfig;
import com.agent.java.model.memory.MemoryItem;
import com.agent.java.model.memory.MemoryType;
import com.agent.java.model.memory.PreferenceUpdateRequest;
import com.agent.java.model.memory.SessionContext;
import com.agent.java.model.memory.UserPreference;
import com.agent.java.model.search.QueryAnalysis;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 记忆管理服务
 * <p>
 * 提供记忆的存储、检索、更新、衰减等核心功能。
 * 使用内存存储（ConcurrentHashMap）实现线程安全的用户记忆管理。
 * 支持记忆衰减机制，记忆的重要性会随时间指数衰减，但访问次数会增强重要性。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    /**
     * 记忆系统配置
     */
    private final MemoryConfig memoryConfig;

    /**
     * 对话总结服务
     */
    private final ConversationSummaryService summaryService;

    /**
     * JSON序列化工具
     */
    private final ObjectMapper objectMapper;

    /**
     * 用户记忆存储
     * <p>
     * key: 用户ID, value: 记忆项列表
     * </p>
     */
    private ConcurrentHashMap<String, List<MemoryItem>> userMemories;

    /**
     * 用户偏好存储
     * <p>
     * key: 用户ID, value: 用户偏好对象
     * </p>
     */
    private ConcurrentHashMap<String, UserPreference> userPreferences;

    /**
     * 会话上下文存储（使用 Caffeine 高性能本地缓存）
     * <p>
     * 配置：
     * - 30分钟不访问自动过期
     * - 最大10000个会话
     * - 自动记录统计指标
     * </p>
     */
    private Cache<String, SessionContext> sessionContexts;

    /**
     * 初始化非依赖注入的字段
     */
    @PostConstruct
    public void init() {
        this.userMemories = new ConcurrentHashMap<>();
        this.userPreferences = new ConcurrentHashMap<>();

        // 使用 Caffeine 初始化会话缓存
        this.sessionContexts = Caffeine.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES) // 30分钟无访问自动过期
                .maximumSize(10000) // 最大10000个会话，超出按LRU淘汰
                .removalListener((key, value, cause) -> log.debug("Session removed: {}, cause: {}", key, cause))
                .build();
    }

    /**
     * 获取用户偏好
     * <p>
     * 如果用户偏好不存在，则创建一个默认的偏好对象
     * </p>
     *
     * @param userId 用户ID
     * @return 用户偏好对象
     */
    public UserPreference getUserPreference(String userId) {
        return userPreferences.computeIfAbsent(userId, id -> {
            UserPreference preference = new UserPreference(id);
            preference.setCommunicationStyle("friendly");
            preference.setLastUpdated(LocalDateTime.now().toString());
            preference.setConfidence(0.5);
            return preference;
        });
    }

    /**
     * 更新用户偏好
     * <p>
     * 支持增量更新：添加/移除品牌、类别、关键词权重等
     * </p>
     *
     * @param userId  用户ID
     * @param request 偏好更新请求
     */
    public void updateUserPreference(String userId, PreferenceUpdateRequest request) {
        UserPreference preference = getUserPreference(userId);

        // 更新品牌偏好
        if (request.getAddBrands() != null) {
            preference.getPreferredBrands().addAll(request.getAddBrands());
            preference.getPreferredBrands().sort(String::compareTo);
        }
        if (request.getRemoveBrands() != null) {
            preference.getPreferredBrands().removeAll(request.getRemoveBrands());
        }

        // 更新类别偏好
        if (request.getAddCategories() != null) {
            preference.getPreferredCategories().addAll(request.getAddCategories());
            preference.getPreferredCategories().sort(String::compareTo);
        }
        if (request.getRemoveCategories() != null) {
            preference.getPreferredCategories().removeAll(request.getRemoveCategories());
        }

        // 更新关键词权重
        if (request.getAddKeywordWeights() != null) {
            preference.getKeywordWeights().putAll(request.getAddKeywordWeights());
        }

        // 更新价格范围
        if (request.getMinPrice() != null) {
            preference.getPriceRange().put("minPrice", request.getMinPrice());
        }
        if (request.getMaxPrice() != null) {
            preference.getPriceRange().put("maxPrice", request.getMaxPrice());
        }

        // 更新沟通风格
        if (request.getCommunicationStyle() != null) {
            preference.setCommunicationStyle(request.getCommunicationStyle());
        }

        // 更新时间和置信度
        preference.setLastUpdated(LocalDateTime.now().toString());
        preference.setConfidence(Math.min(1.0, preference.getConfidence() + 0.1));

        userPreferences.put(userId, preference);
        log.info("Updated preference for user: {}", userId);
    }

    /**
     * 记录搜索记忆
     * <p>
     * 将用户的搜索查询和查询分析结果记录到记忆系统，并自动学习关键词偏好
     * </p>
     *
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @param query     搜索查询
     * @param analysis  查询分析结果
     */
    public void recordSearch(String userId, String sessionId, String query, QueryAnalysis analysis) {
        try {
            MemoryItem memory = new MemoryItem();
            memory.setMemoryId(UUID.randomUUID().toString());
            memory.setUserId(userId);
            memory.setSessionId(sessionId);
            memory.setType(MemoryType.SEARCH_HISTORY);
            memory.setContent(objectMapper.writeValueAsString(analysis));
            memory.setImportance(0.6);
            memory.setDecayFactor(memoryConfig.getDefaultDecayFactor());
            memory.setMetadata(query);

            addMemory(userId, memory);

            // 自动学习关键词偏好
            if (analysis.getKeywords() != null) {
                for (String keyword : analysis.getKeywords()) {
                    updateKeywordWeight(userId, keyword, 0.1);
                }
            }

            // 更新近期搜索关键词
            updateRecentSearches(userId, query);

            log.debug("Recorded search memory for user: {}, query: {}", userId, query);
        } catch (JsonProcessingException e) {
            log.error("Failed to record search memory", e);
        }
    }

    /**
     * 记录用户反馈
     * <p>
     * 记录用户对搜索结果的评分反馈，用于优化搜索结果排序
     * </p>
     *
     * @param userId     用户ID
     * @param documentId 商品ID
     * @param score      评分（0-5）
     */
    public void recordFeedback(String userId, String documentId, int score) {
        try {
            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put("documentId", documentId);
            feedbackData.put("score", score);

            MemoryItem memory = new MemoryItem();
            memory.setMemoryId(UUID.randomUUID().toString());
            memory.setUserId(userId);
            memory.setType(MemoryType.FEEDBACK);
            memory.setContent(objectMapper.writeValueAsString(feedbackData));
            memory.setImportance(0.85);
            memory.setDecayFactor(memoryConfig.getDefaultDecayFactor());

            addMemory(userId, memory);
            log.info("Recorded feedback for user: {}, document: {}, score: {}", userId, documentId, score);
        } catch (JsonProcessingException e) {
            log.error("Failed to record feedback", e);
        }
    }

    /**
     * 获取用户的所有记忆
     * <p>
     * 返回用户的记忆列表，按重要性降序排列。
     * 查询时会记录访问（更新访问时间和次数，并给予访问奖励）。
     * </p>
     *
     * @param userId 用户ID
     * @return 记忆项列表（按重要性降序）
     */
    public List<MemoryItem> getUserMemories(String userId) {
        List<MemoryItem> memories = userMemories.getOrDefault(userId, new ArrayList<>());
        return memories.stream()
                .map(this::recordAccess) // 记录访问，给予访问奖励
                .sorted(Comparator.comparingDouble(MemoryItem::getImportance).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 记录记忆被访问（仅在查询时调用）
     * <p>
     * 更新访问时间、增加访问次数，并给予访问奖励（小幅提升重要性）。
     * </p>
     *
     * @param memory 记忆项
     * @return 更新后的记忆项
     */
    private MemoryItem recordAccess(MemoryItem memory) {
        memory.setLastAccessedAt(LocalDateTime.now());
        memory.setAccessCount(memory.getAccessCount() + 1);

        // 访问奖励：小幅提升重要性（最多提升到1.0）
        memory.setImportance(Math.min(1.0, memory.getImportance() + 0.05));

        return memory;
    }

    /**
     * 获取相关记忆（用于搜索上下文）
     * <p>
     * 筛选与搜索相关的记忆类型，用于增强搜索效果
     * </p>
     *
     * @param userId 用户ID
     * @param query  搜索查询
     * @param limit  返回数量限制
     * @return 相关记忆项列表
     */
    public List<MemoryItem> getRelevantMemories(String userId, String query, int limit) {
        List<MemoryItem> memories = getUserMemories(userId);
        return memories.stream()
                .filter(m -> m.getType() == MemoryType.SEARCH_HISTORY ||
                        m.getType() == MemoryType.PREFERENCE ||
                        m.getType() == MemoryType.INTERACTION)
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 清空用户记忆
     * <p>
     * 删除用户的所有记忆和偏好信息
     * </p>
     *
     * @param userId 用户ID
     */
    public void clearUserMemory(String userId) {
        userMemories.remove(userId);
        userPreferences.remove(userId);
        log.info("Cleared memory for user: {}", userId);
    }

    // ========== 会话上下文管理方法 ==========

    /**
     * 添加对话记录
     * <p>
     * 记录用户输入和助手回复到会话上下文，并定期触发LLM总结。
     * </p>
     *
     * @param sessionId         会话ID
     * @param userId            用户ID
     * @param userQuery         用户输入
     * @param assistantResponse 助手回复
     */
    public void addConversation(String sessionId, String userId, String userQuery, String assistantResponse) {
        // 获取或创建会话上下文
        SessionContext context = sessionContexts.get(sessionId, k -> new SessionContext(sessionId, userId));

        // 创建用户消息记忆
        MemoryItem userMemory = new MemoryItem();
        userMemory.setMemoryId(UUID.randomUUID().toString());
        userMemory.setUserId(userId);
        userMemory.setSessionId(sessionId);
        userMemory.setType(MemoryType.CONVERSATION_HISTORY);
        userMemory.setRole("USER");
        userMemory.setContent(userQuery);
        userMemory.setImportance(0.8);
        userMemory.setTurnNumber(context.getTurnCount() + 1);

        // 创建助手回复记忆
        MemoryItem assistantMemory = new MemoryItem();
        assistantMemory.setMemoryId(UUID.randomUUID().toString());
        assistantMemory.setUserId(userId);
        assistantMemory.setSessionId(sessionId);
        assistantMemory.setType(MemoryType.CONVERSATION_HISTORY);
        assistantMemory.setRole("ASSISTANT");
        assistantMemory.setContent(assistantResponse);
        assistantMemory.setImportance(0.7);
        assistantMemory.setTurnNumber(context.getTurnCount() + 1);

        // 添加到对话历史
        context.getConversationHistory().add(userMemory);
        context.getConversationHistory().add(assistantMemory);
        context.setTurnCount(context.getTurnCount() + 1);
        context.setLastActivityTime(LocalDateTime.now());

        // 更新回缓存（确保缓存的对象本身没有问题，因为 SessionContext 是可变的）
        sessionContexts.put(sessionId, context);

        // 每5轮对话触发一次总结
        if (context.getTurnCount() % 5 == 0) {
            summarizeConversation(sessionId);
        }

        log.debug("Added conversation for session: {}, turn: {}", sessionId, context.getTurnCount());
    }

    /**
     * 获取对话上下文
     * <p>
     * 返回指定数量的最近对话轮次，用于搜索和回复生成。
     * </p>
     *
     * @param sessionId 会话ID
     * @param maxTurns  最大返回轮数
     * @return 对话记忆列表
     */
    public List<MemoryItem> getConversationContext(String sessionId, int maxTurns) {
        SessionContext context = sessionContexts.getIfPresent(sessionId);
        if (context == null || context.getConversationHistory().isEmpty()) {
            return new ArrayList<>();
        }

        List<MemoryItem> history = context.getConversationHistory();
        // 返回最近的 N 轮对话（每轮包含 user+assistant）
        int startIndex = Math.max(0, history.size() - maxTurns * 2);
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    /**
     * 获取会话的最新总结
     *
     * @param sessionId 会话ID
     * @return 总结记忆（如果存在）
     */
    public MemoryItem getLatestSummary(String sessionId) {
        SessionContext context = sessionContexts.getIfPresent(sessionId);
        return context != null ? context.getLatestSummary() : null;
    }

    /**
     * 使用LLM总结对话
     * <p>
     * 对对话历史进行总结，并将重要信息升级为长期偏好。
     * </p>
     *
     * @param sessionId 会话ID
     */
    private void summarizeConversation(String sessionId) {
        SessionContext context = sessionContexts.getIfPresent(sessionId);
        if (context == null || context.getConversationHistory().isEmpty()) {
            return;
        }

        try {
            // 调用LLM总结服务
            String summaryText = summaryService.summarize(context.getConversationHistory());

            if (summaryText != null && !summaryText.isEmpty()) {
                // 创建总结记忆
                MemoryItem summaryMemory = new MemoryItem();
                summaryMemory.setMemoryId(UUID.randomUUID().toString());
                summaryMemory.setUserId(context.getUserId());
                summaryMemory.setSessionId(sessionId);
                summaryMemory.setType(MemoryType.CONVERSATION_SUMMARY);
                summaryMemory.setContent(summaryText);
                summaryMemory.setImportance(0.9);

                context.setLatestSummary(summaryMemory);

                // 更新回缓存
                sessionContexts.put(sessionId, context);

                // 将重要信息升级为长期偏好
                upgradeToLongTermMemory(context.getUserId(), summaryText);

                log.info("Generated conversation summary for session: {}", sessionId);
            }
        } catch (Exception e) {
            log.error("Failed to summarize conversation for session: {}", sessionId, e);
        }
    }

    /**
     * 将对话要点升级为长期偏好
     * <p>
     * 从对话总结中提取关键词，更新用户的长期偏好。
     * </p>
     *
     * @param userId  用户ID
     * @param summary 对话总结文本
     */
    private void upgradeToLongTermMemory(String userId, String summary) {
        List<String> keywords = summaryService.extractKeywords(summary);
        for (String keyword : keywords) {
            updateKeywordWeight(userId, keyword, 0.05);
        }
    }

    /**
     * 清理会话上下文
     * <p>
     * 删除指定会话的所有上下文信息。
     * </p>
     *
     * @param sessionId 会话ID
     */
    public void clearSessionContext(String sessionId) {
        sessionContexts.invalidate(sessionId);
        log.info("Cleared session context: {}", sessionId);
    }

    /**
     * 定时清理过期记忆
     * <p>
     * 每小时执行一次，执行以下操作：
     * 1. 计算所有记忆的衰减值（更新重要性）
     * 2. 过滤过期记忆（创建时间超过阈值）
     * 3. 过滤重要性过低的记忆（低于阈值）
     * 4. 按 LRU + 重要性排序，保留前N条
     * </p>
     */
    @Scheduled(fixedRate = 3600000)
    public void cleanExpiredMemories() {
        log.info("Starting expired memory cleanup...");

        LocalDateTime expireThreshold = LocalDateTime.now().minusDays(memoryConfig.getMemoryExpireDays());
        int cleanedCount = 0;

        for (Map.Entry<String, List<MemoryItem>> entry : userMemories.entrySet()) {
            String userId = entry.getKey();
            List<MemoryItem> memories = entry.getValue();

            // 步骤1: 计算所有记忆的衰减值（仅更新重要性，不改变访问状态）
            for (MemoryItem memory : memories) {
                updateDecayedImportance(memory);
            }

            // 步骤2: 过滤过期+低重要性记忆，按 LRU + 重要性排序
            List<MemoryItem> validMemories = memories.stream()
                    .filter(m -> m.getCreatedAt().isAfter(expireThreshold)) // 时间过期过滤
                    .filter(m -> m.getImportance() >= 0.05) // 重要性阈值过滤（低于0.05视为应被遗忘）
                    .sorted(Comparator
                            .comparing(MemoryItem::getLastAccessedAt).reversed() // LRU优先：最近访问的排前面
                            .thenComparing(MemoryItem::getImportance).reversed()) // 同时间下：重要性高的排前面
                    .limit(memoryConfig.getMaxMemoriesPerUser())
                    .collect(Collectors.toList());

            int removed = memories.size() - validMemories.size();
            if (removed > 0) {
                cleanedCount += removed;
                userMemories.put(userId, validMemories);
            }
        }

        log.info("Memory cleanup completed, removed {} expired/low-importance memories", cleanedCount);
    }

    // ========== 私有方法 ==========

    /**
     * 添加记忆到存储
     * <p>
     * 自动检查数量限制，超出时删除最旧的记忆
     * </p>
     *
     * @param userId 用户ID
     * @param memory 记忆项
     */
    private void addMemory(String userId, MemoryItem memory) {
        userMemories.computeIfAbsent(userId, k -> new ArrayList<>()).add(memory);

        // 检查数量限制，超出则删除最旧的
        List<MemoryItem> memories = userMemories.get(userId);
        if (memories.size() > memoryConfig.getMaxMemoriesPerUser()) {
            memories.sort(Comparator.comparing(MemoryItem::getLastAccessedAt));
            memories.remove(0);
        }
    }

    /**
     * 计算并更新记忆的衰减值（仅在定时任务中调用）
     * <p>
     * 使用指数衰减公式计算记忆的当前重要性，并根据历史访问次数给予奖励。
     * 注意：此方法仅更新重要性，不改变访问时间和访问次数。
     * </p>
     *
     * @param memory 记忆项
     */
    private void updateDecayedImportance(MemoryItem memory) {
        LocalDateTime now = LocalDateTime.now();
        long daysSinceCreation = ChronoUnit.DAYS.between(memory.getCreatedAt(), now);

        // 指数衰减：importance * decayFactor^days
        double decayedImportance = memory.getImportance() *
                Math.pow(memory.getDecayFactor(), daysSinceCreation);

        // 历史访问次数奖励（基于累积访问次数，最多加0.3）
        double accessBonus = Math.min(0.3, memory.getAccessCount() * 0.05);

        // 仅更新重要性，不改变 lastAccessedAt 和 accessCount
        memory.setImportance(Math.min(1.0, decayedImportance + accessBonus));
    }

    /**
     * 更新关键词权重
     * <p>
     * 增量更新关键词的权重值
     * </p>
     *
     * @param userId  用户ID
     * @param keyword 关键词
     * @param delta   权重增量
     */
    private void updateKeywordWeight(String userId, String keyword, double delta) {
        UserPreference preference = getUserPreference(userId);
        Map<String, Double> keywordWeights = preference.getKeywordWeights();
        double currentWeight = keywordWeights.getOrDefault(keyword, 0.0);
        keywordWeights.put(keyword, Math.min(1.0, currentWeight + delta));
        preference.setLastUpdated(LocalDateTime.now().toString());
        userPreferences.put(userId, preference);
    }

    /**
     * 更新近期搜索关键词
     * <p>
     * 维护用户近期搜索的关键词列表，用于个性化推荐
     * </p>
     *
     * @param userId 用户ID
     * @param query  搜索查询
     */
    private void updateRecentSearches(String userId, String query) {
        UserPreference preference = getUserPreference(userId);
        List<String> recentSearches = preference.getRecentSearchedKeywords();

        // 移除已存在的（避免重复）
        recentSearches.remove(query);
        // 添加到最前面
        recentSearches.add(0, query);
        // 限制数量
        if (recentSearches.size() > memoryConfig.getRecentSearchesLimit()) {
            recentSearches = recentSearches.subList(0, memoryConfig.getRecentSearchesLimit());
        }

        preference.setRecentSearchedKeywords(recentSearches);
        preference.setLastUpdated(LocalDateTime.now().toString());
        userPreferences.put(userId, preference);
    }
}
