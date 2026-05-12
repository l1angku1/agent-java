package com.agent.java.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Jackson配置类
 * <p>
 * 提供统一配置的 ObjectMapper Bean，用于 JSON 序列化和反序列化。
 * </p>
 */
@Slf4j
@Configuration
public class JacksonConfig {

    /**
     * 创建 ObjectMapper Bean
     *
     * @return ObjectMapper实例
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
