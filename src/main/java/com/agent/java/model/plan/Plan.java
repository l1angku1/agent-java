package com.agent.java.model.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
                .planId(UUID.randomUUID().toString())
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
        Step step = Step.builder()
                .stepId(UUID.randomUUID().toString())
                .name(name)
                .instruction(instruction)
                .outputKey(outputKey)
                .build();
        this.steps.add(step);
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

    /**
     * 计划步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Step {

        /** 步骤唯一标识 */
        private String stepId;

        /** 步骤名称 */
        private String name;

        /** 步骤指令，包含该步骤需要完成的具体任务 */
        private String instruction;

        /** 结果输出的key，会存入context中供后续步骤引用 */
        private String outputKey;

        /** 步骤执行结果 */
        private String result;

        /** 步骤开始时间 */
        private LocalDateTime startTime;

        /** 步骤结束时间 */
        private LocalDateTime endTime;

        /**
         * 计算步骤执行时长（秒）
         */
        public long getDurationSeconds() {
            if (startTime == null || endTime == null) {
                return 0;
            }
            return java.time.Duration.between(startTime, endTime).getSeconds();
        }
    }
}