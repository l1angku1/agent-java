package com.agent.java.service;

import java.time.Duration;

import org.springframework.stereotype.Service;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * AI服务封装类
 * 提供统一的大模型调用接口，用于AI搜索流程中的各个阶段
 */
@Slf4j
@Service
public class AIService {

    private final OpenAIChatModel chatModel;

    /**
     * 构造函数，注入OpenAIChatModel
     * 
     * @param chatModel OpenAIChatModel实例
     */
    public AIService(OpenAIChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 创建Agent
     * 
     * @return ReActAgent实例
     */
    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("AISearchAgent")
                .sysPrompt(
                        "你是一个专业的AI搜索助手。你擅长以下任务:\n" +
                                "1. 查询分析: 分析用户查询的意图和关键词\n" +
                                "2. 文档重排: 根据相关性对文档进行评分排序\n" +
                                "3. 质量评估: 评估检索结果的质量指标\n" +
                                "4. 回答生成: 基于参考文档生成准确的回答\n" +
                                "请根据用户的具体请求, 以JSON格式或自然语言形式输出结果。")
                .model(chatModel)
                .build();
    }

    /**
     * 调用大模型生成响应
     * 
     * @param prompt 提示词
     * @return 模型响应内容
     */
    public String generate(String prompt) {
        log.debug("AI Service generating response for prompt (length: {})", prompt.length());

        ReActAgent agent = createAgent();

        Msg request = Msg.builder()
                .textContent(prompt)
                .build();

        Msg response = agent.call(request).block(Duration.ofMinutes(5));

        if (response != null && response.getTextContent() != null) {
            log.debug("AI Service response received (length: {})", response.getTextContent().length());
            return response.getTextContent().trim();
        }

        log.warn("AI Service returned empty response");
        return null;
    }
}