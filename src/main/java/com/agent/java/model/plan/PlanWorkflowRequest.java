package com.agent.java.model.plan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计划工作流请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanWorkflowRequest {

    /**
     * 工作流模式：preview, confirm, cancel, status
     */
    private String mode;

    /**
     * 用户请求内容（preview模式必填）
     */
    private String request;

    /**
     * 计划ID（confirm/cancel/status模式必填）
     */
    private String planId;

    /**
     * 验证请求参数
     */
    public String validate() {
        if (mode == null || mode.isBlank()) {
            return "mode 参数不能为空";
        }

        switch (mode) {
            case "preview":
                if (request == null || request.isBlank()) {
                    return "preview 模式下 request 参数不能为空";
                }
                break;
            case "confirm":
            case "cancel":
            case "status":
                if (planId == null || planId.isBlank()) {
                    return mode + " 模式下 planId 参数不能为空";
                }
                break;
            default:
                return "无效的 mode 参数: " + mode;
        }
        return null;
    }
}
