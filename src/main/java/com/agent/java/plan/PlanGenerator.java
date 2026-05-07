package com.agent.java.plan;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.agent.java.model.plan.PlanRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 计划生成器
 * 使用LLM自动将用户需求拆解成可执行的计划步骤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanGenerator {

    private final OpenAIChatModel chatModel;

    /** 任务规划的Prompt模板 */
    private static final String PLANNING_PROMPT = """
            你是一个任务规划专家。当用户提出一个需求或问题时，你需要将其拆解成多个有序的执行步骤。

            请按照以下JSON格式输出计划(只输出JSON, 不要其他内容):
            {
                "name": "计划名称",
                "description": "计划简短描述",
                "originalRequest": "用户的原始需求",
                "steps": [
                    {
                        "name": "步骤1名称",
                        "instruction": "步骤1的详细指令, 包含该步骤需要完成的具体任务",
                        "outputKey": "step1_result"
                    },
                    {
                        "name": "步骤2名称",
                        "instruction": "步骤2的详细指令，可以使用 {step1_result} 来引用前序步骤的结果",
                        "outputKey": "step2_result"
                    }
                ]
            }

            拆分原则:
            1. 每个步骤应该是独立的、可执行的任务单元
            2. 步骤之间有明确的依赖关系，后续步骤可以用 {前序outputKey} 引用前序结果
            3. 通常 3-7 个步骤比较合适，复杂任务也不要超过 10 个步骤
            4. 第一步通常是信息收集或问题理解，后续步骤是分析、执行、汇总等

            现在请分析以下用户需求并生成计划:
            """;

    /**
     * 根据用户请求生成计划
     *
     * @param userRequest 用户需求
     * @return 生成的计划请求
     */
    public PlanRequest generatePlan(String userRequest) {
        try {
            ReActAgent agent = createAgent();

            String fullPrompt = PLANNING_PROMPT + "\n\n用户需求: " + userRequest;

            Msg userMsg = Msg.builder()
                    .textContent(fullPrompt)
                    .build();

            Msg response = agent.call(userMsg).block(Duration.ofSeconds(60));

            String content = response != null ? response.getTextContent() : "";
            return parsePlanFromResponse(content);

        } catch (Exception e) {
            log.error("计划生成失败", e);
            throw new RuntimeException("计划生成失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步生成计划
     */
    public Mono<PlanRequest> generatePlanAsync(String userRequest) {
        return Mono.fromCallable(() -> generatePlan(userRequest))
                .onErrorResume(e -> {
                    log.error("异步计划生成失败", e);
                    return Mono.error(e);
                });
    }

    /**
     * 从LLM响应中解析JSON计划
     */
    private PlanRequest parsePlanFromResponse(String content) {
        try {
            String json = extractJson(content);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, PlanRequest.class);
        } catch (Exception e) {
            log.error("解析计划JSON失败: {}", content, e);
            throw new RuntimeException("解析计划失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从响应内容中提取JSON字符串
     */
    private String extractJson(String content) {
        String trimmed = content.trim();

        int startIdx = trimmed.indexOf("{");
        int endIdx = trimmed.lastIndexOf("}");

        if (startIdx >= 0 && endIdx > startIdx) {
            return trimmed.substring(startIdx, endIdx + 1);
        }

        throw new RuntimeException("无法从响应中提取JSON: " + content);
    }

    /**
     * 创建规划Agent
     */
    private ReActAgent createAgent() {
        return ReActAgent.builder()
                .name("PlanGeneratorAgent")
                .sysPrompt("你是一个专业的任务规划专家, 擅长将复杂需求拆解成可执行的步骤。你的输出必须是有效的JSON格式计划。")
                .model(chatModel)
                .build();
    }
}