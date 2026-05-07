package com.agent.java.model.knowledge;

import lombok.Data;

/**
 * 知识查询请求模型
 * <p>
 * 用于接收用户知识查询请求。
 * </p>
 */
@Data
public class KnowledgeRequest {

    /**
     * 用户查询内容
     */
    private String query;

}