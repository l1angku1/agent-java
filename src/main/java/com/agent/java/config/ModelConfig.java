package com.agent.java.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class ModelConfig {

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model}")
    private String modelName;

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