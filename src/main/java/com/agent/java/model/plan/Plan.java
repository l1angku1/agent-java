package com.agent.java.model.plan;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计划模型，包含多个有序步骤和执行上下文
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /** 计划唯一标识 */
    private String planId;

    /** 计划名称 */
    private String name;

    /** 计划简短描述 */
    private String description;

    /** 用户原始需求 */
    private String originalRequest;

    /** 计划状态 */
    private PlanStatus status;

    /** 有序步骤列表 */
    private List<Step> steps;

    /** 步骤间共享的上下文数据，key为outputKey，value为步骤执行结果 */
    private Map<String, Object> context;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 执行完成时间 */
    private LocalDateTime completedAt;

    /** 错误信息（执行失败时） */
    private String errorMessage;

    /**
     * 创建新计划
     */
    public static Plan create(String name, String description, String originalRequest) {
        return Plan.builder()
                .planId(UUID.randomUUID().toString().replace("-", ""))
                .name(name)
                .description(description)
                .originalRequest(originalRequest)
                .status(PlanStatus.CREATED)
                .steps(new ArrayList<>())
                .context(new HashMap<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 添加步骤到计划
     */
    public void addStep(String name, String instruction, String outputKey) {
        steps.add(Step.create(name, instruction, outputKey));
    }

    /**
     * 添加步骤到计划（带完整配置）
     */
    public void addStep(String name, String instruction, String outputKey,
            List<String> dependsOn, int maxRetries,
            long timeoutSeconds, String failStrategy) {
        steps.add(Step.create(name, instruction, outputKey, dependsOn, maxRetries, timeoutSeconds, failStrategy));
    }

    /**
     * 获取当前待执行的步骤（第一个result为null的步骤）
     */
    public Step getCurrentStep() {
        return steps.stream()
                .filter(s -> s.getResult() == null)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取已完成步骤数量
     */
    public int getCompletedStepCount() {
        return (int) steps.stream().filter(s -> s.getResult() != null).count();
    }
}
