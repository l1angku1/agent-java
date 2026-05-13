package com.agent.java.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API通用响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse {

    /**
     * 状态
     */
    private String status;

    /**
     * 消息
     */
    private String message;
}