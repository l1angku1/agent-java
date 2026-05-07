package com.agent.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * AI模型配置类
 * <p>
 * 用于配置 OpenAI ChatModel 的连接参数，包括 API Key、Base URL 和模型名称。
 * </p>
 */
@Slf4j
@Configuration
public class ModelConfig {

    /** OpenAI API Key */
    @Value("${ai.openai.api-key}")
    private String apiKey;

    /** OpenAI API Base URL */
    @Value("${ai.openai.base-url}")
    private String baseUrl;

    /** OpenAI 模型名称 */
    @Value("${ai.openai.model}")
    private String modelName;

    /**
     * 创建 OpenAI ChatModel Bean
     *
     * @return OpenAIChatModel实例
     */
    @Bean
    public OpenAIChatModel chatModel() {
        String effectiveBaseUrl = baseUrl;
        if (effectiveBaseUrl != null && !effectiveBaseUrl.endsWith("/v1") && !effectiveBaseUrl.endsWith("/v1/")) {
            effectiveBaseUrl = effectiveBaseUrl.endsWith("/") ? effectiveBaseUrl + "v1" : effectiveBaseUrl + "/v1";
        }

        return OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(effectiveBaseUrl)
                .modelName(modelName)
                .build();
    }
}