package com.agent.java.model.memory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 会话上下文模型
 * <p>
 * 用于存储和管理短期会话级别的对话记忆，支持多轮对话功能。
 * 包含原始对话历史、LLM总结、会话状态等信息。
 * </p>
 */
@Data
public class SessionContext {

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 对话历史列表
     * <p>
     * 按时间顺序存储用户输入和助手回复
     * </p>
     */
    private List<MemoryItem> conversationHistory;

    /**
     * 最新的对话总结
     * <p>
     * 由LLM定期生成，用于压缩上下文长度
     * </p>
     */
    private MemoryItem latestSummary;

    /**
     * 最后活动时间
     * <p>
     * 用于判断会话是否过期
     * </p>
     */
    private LocalDateTime lastActivityTime;

    /**
     * 对话轮数
     * <p>
     * 用于触发定期总结
     * </p>
     */
    private int turnCount;

    /**
     * 默认构造函数
     */
    public SessionContext() {
        this.conversationHistory = new ArrayList<>();
        this.lastActivityTime = LocalDateTime.now();
        this.turnCount = 0;
    }

    /**
     * 构造函数
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    public SessionContext(String sessionId, String userId) {
        this();
        this.sessionId = sessionId;
        this.userId = userId;
    }
}
