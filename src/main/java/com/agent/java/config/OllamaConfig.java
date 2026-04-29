package com.agent.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Ollama 向量化配置类
 */
@Data
@Component
public class OllamaConfig {

    /**
     * Ollama 服务地址
     */
    @Value("${ollama.embedding.base-url}")
    private String baseUrl;

    /**
     * 向量化模型名称
     */
    @Value("${ollama.embedding.model-name}")
    private String modelName;

    /**
     * 向量维度
     */
    @Value("${ollama.embedding.dimensions}")
    private int dimensions;
}