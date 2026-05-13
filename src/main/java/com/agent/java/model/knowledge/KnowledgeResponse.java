package com.agent.java.model.knowledge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识查询响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeResponse {

    /**
     * 查询回答内容
     */
    private String answer;

    /**
     * 内容来源文件名
     */
    private String source;
}