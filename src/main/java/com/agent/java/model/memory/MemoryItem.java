package com.agent.java.model.memory;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 记忆项模型
 * <p>
 * 表示记忆系统中的单个记忆条目，包含记忆的基本信息和重要性计算相关字段。
 * 支持记忆衰减机制，记忆的重要性会随时间衰减，但访问次数会增强重要性。
 * </p>
 */
@Data
public class MemoryItem {

    /**
     * 记忆唯一标识符
     */
    private String memoryId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话ID（可选）
     */
    private String sessionId;

    /**
     * 记忆类型
     */
    private MemoryType type;

    /**
     * 记忆内容（通常为JSON格式的字符串）
     */
    private String content;

    /**
     * 元数据（如搜索关键词等附加信息）
     */
    private String metadata;

    /**
     * 重要性分数（0.0-1.0）
     */
    private double importance;

    /**
     * 衰减因子（0.0-1.0）
     */
    private double decayFactor;

    /**
     * 访问次数
     */
    private int accessCount;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 角色类型（USER/ASSISTANT/SYSTEM）
     * <p>用于标识对话中的角色</p>
     */
    private String role;

    /**
     * 对话轮次
     * <p>用于标识在对话中的顺序位置</p>
     */
    private int turnNumber;

    /**
     * 是否已被总结过
     * <p>用于避免重复总结</p>
     */
    private boolean summarized;

    /**
     * 默认构造函数
     * <p>初始化创建时间、最后访问时间为当前时间，访问次数为1</p>
     */
    public MemoryItem() {
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount = 1;
        this.turnNumber = 0;
        this.summarized = false;
    }
}
