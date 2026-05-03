package com.agent.java.model.memory;

/**
 * 记忆类型枚举
 * <p>
 * 定义记忆系统中存储的记忆类型，用于区分不同类型的用户数据。
 * </p>
 */
public enum MemoryType {

    /**
     * 搜索历史
     * <p>记录用户的搜索查询和查询分析结果</p>
     */
    SEARCH_HISTORY,

    /**
     * 用户偏好
     * <p>存储用户的长期偏好信息，如品牌偏好、类别偏好等</p>
     */
    PREFERENCE,

    /**
     * 用户反馈
     * <p>记录用户对搜索结果的点击、评分等反馈信息</p>
     */
    FEEDBACK,

    /**
     * 交互上下文
     * <p>记录用户与系统的交互对话上下文</p>
     */
    INTERACTION,

    /**
     * 对话历史
     * <p>存储原始的多轮对话记录（用户输入和助手回复）</p>
     */
    CONVERSATION_HISTORY,

    /**
     * 对话总结
     * <p>由LLM总结的对话要点，用于压缩上下文长度</p>
     */
    CONVERSATION_SUMMARY
}
