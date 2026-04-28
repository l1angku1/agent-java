package com.agent.java.model.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建计划的请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRequest {

    /** 计划名称 */
    private String name;

    /** 计划描述 */
    private String description;

    /** 用户原始需求 */
    private String originalRequest;

    /** 步骤配置列表 */
    private List<StepConfig> steps;

    /**
     * 步骤配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepConfig {

        /** 步骤名称 */
        private String name;

        /** 步骤指令，可使用 {key} 引用前序步骤结果 */
        private String instruction;

        /** 结果输出的key */
        private String outputKey;
    }
}