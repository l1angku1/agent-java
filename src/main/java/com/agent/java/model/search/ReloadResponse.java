package com.agent.java.model.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品数据重载响应模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReloadResponse {

    /**
     * 操作状态
     */
    private String status;

    /**
     * 商品数量
     */
    private String goodsCount;

    /**
     * 操作消息
     */
    private String message;
}
