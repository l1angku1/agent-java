package com.agent.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

/**
 * 记忆系统配置类
 * <p>
 * 用于配置记忆系统的各项参数，支持通过 application.yml 配置文件进行外部配置。
 * 使用 @Value 注解进行配置注入，所有配置项均有默认值。
 * </p>
 */
@Data
@Configuration
public class MemoryConfig {

    /**
     * 是否启用记忆功能
     * <p>默认值: true</p>
     */
    @Value("${memory.enabled:true}")
    private boolean enabled;

    /**
     * 默认记忆衰减因子
     * <p>范围: 0.0-1.0，值越小衰减越快，值越大记忆保留时间越长</p>
     * <p>默认值: 0.9</p>
     */
    @Value("${memory.default-decay-factor:0.9}")
    private double defaultDecayFactor;

    /**
     * 单个用户最大记忆数量限制
     * <p>超过此数量时，会自动删除最旧的记忆</p>
     * <p>默认值: 100</p>
     */
    @Value("${memory.max-memories-per-user:100}")
    private int maxMemoriesPerUser;

    /**
     * 记忆过期天数
     * <p>超过此天数的记忆会被定时任务清理</p>
     * <p>默认值: 30</p>
     */
    @Value("${memory.memory-expire-days:30}")
    private int memoryExpireDays;

    /**
     * 近期搜索关键词保留数量
     * <p>用于记录用户近期搜索的关键词，用于个性化推荐</p>
     * <p>默认值: 10</p>
     */
    @Value("${memory.recent-searches-limit:10}")
    private int recentSearchesLimit;
}
