package com.agent.java.model.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动计划请求模型
 * 用于 /auto 和 /generate 接口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanAutoRequest {

    /**
     * 用户请求内容（必填）
     */
    private String request;

    /**
     * 验证请求参数
     */
    public String validate() {
        if (request == null || request.isBlank()) {
            return "请求内容不能为空";
        }
        return null;
    }
}
