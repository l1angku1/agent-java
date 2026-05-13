package com.agent.java.model.plan;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计划操作响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {

    /**
     * 操作是否成功
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 计划ID
     */
    private String planId;

    /**
     * 计划详情
     */
    private Plan plan;

    /**
     * 计划步骤列表
     */
    private List<?> steps;

    /**
     * 计划状态
     */
    private String status;
}