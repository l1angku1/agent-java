package com.agent.java.model.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户反馈请求模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackRequest {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 文档ID
     */
    private String documentId;

    /**
     * 评分
     */
    private Integer score;
}