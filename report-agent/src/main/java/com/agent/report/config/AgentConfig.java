package com.agent.report.config;

import com.agent.report.tool.EmailTool;
import com.agent.report.tool.GitLabTool;
import com.agent.report.tool.WeatherTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String modelName;

    private OpenAIChatModel createModel() {
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

    @Bean
    public ReActAgent weatherAgent(WeatherTool weatherTool) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(weatherTool);

        return ReActAgent.builder()
                .name("WeatherAgent")
                .sysPrompt("你是一个专业的天气助手。请参考 ReAct (Reasoning and Acting) 模式，" +
                        "通过调用 get_weather 工具来回答用户关于天气的问题。")
                .model(createModel())
                .toolkit(toolkit)
                .build();
    }

    @Bean
    public ReActAgent reportAgent(GitLabTool gitLabTool, EmailTool emailTool) {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(gitLabTool);
        toolkit.registerTool(emailTool);

        return ReActAgent.builder()
                .name("ReportAgent")
                .sysPrompt("你是一个日报助手。你的任务是：\n" +
                        "1. 使用 get_gitlab_commits 获取指定日期的 git 提交记录。\n" +
                        "2. 结合用户手动补充的内容，汇总并生成一份结构清晰的日报。\n" +
                        "3. 使用 send_email 将日报发送给指定的领导。\n" +
                        "请参考 ReAct (Reasoning and Acting) 模式，思考每一步的操作。")
                .model(createModel())
                .toolkit(toolkit)
                .build();
    }
}
