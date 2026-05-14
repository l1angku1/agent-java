package com.agent.java.model.plan;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计划步骤
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Step {

    /** 步骤唯一标识 */
    private String stepId;

    /** 步骤名称 */
    private String name;

    /** 步骤指令，包含该步骤需要完成的具体任务 */
    private String instruction;

    /** 结果输出的key，会存入context中供后续步骤引用 */
    private String outputKey;

    /** 依赖的前置步骤 outputKey 列表 */
    private List<String> dependsOn;

    /** 步骤执行结果 */
    private String result;

    /** 步骤开始时间 */
    private LocalDateTime startTime;

    /** 步骤结束时间 */
    private LocalDateTime endTime;

    /** 最大重试次数 */
    private int maxRetries;

    /** 超时时间（秒） */
    private long timeoutSeconds;

    /** 失败策略: "skip", "abort", "fallback" */
    private String failStrategy;

    /**
     * 创建步骤
     */
    public static Step create(String name, String instruction, String outputKey) {
        return create(name, instruction, outputKey, null, 0, 120, "abort");
    }

    /**
     * 创建步骤（带完整配置）
     */
    public static Step create(String name, String instruction, String outputKey,
            List<String> dependsOn, int maxRetries,
            long timeoutSeconds, String failStrategy) {
        return Step.builder()
                .stepId(UUID.randomUUID().toString())
                .name(name)
                .instruction(instruction)
                .outputKey(outputKey)
                .dependsOn(dependsOn)
                .maxRetries(maxRetries)
                .timeoutSeconds(timeoutSeconds > 0 ? timeoutSeconds : 120)
                .failStrategy(failStrategy != null ? failStrategy : "abort")
                .build();
    }

    /**
     * 计算步骤执行时长（秒）
     */
    public long getDurationSeconds() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        return Duration.between(startTime, endTime).getSeconds();
    }
}
