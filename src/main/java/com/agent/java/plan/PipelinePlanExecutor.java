package com.agent.java.plan;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.agent.java.model.plan.Plan;
import com.agent.java.model.plan.PlanRequest;
import com.agent.java.model.plan.PlanRequest.StepConfig;
import com.agent.java.model.plan.PlanStatus;
import com.agent.java.tool.FileSystemTools;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Pipeline计划执行器
 * 负责按顺序执行计划中的各个步骤
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelinePlanExecutor {

    private final OpenAIChatModel chatModel;
    private final FileSystemTools fileSystemTools;

    /** 正在运行的计划集合 */
    private final Map<String, Plan> runningPlans = new ConcurrentHashMap<>();

    /**
     * 根据请求创建计划
     */
    public Plan createPlan(PlanRequest request) {
        Plan plan = Plan.create(request.getName(), request.getDescription(), request.getOriginalRequest());

        for (StepConfig stepConfig : request.getSteps()) {
            plan.addStep(stepConfig.getName(), stepConfig.getInstruction(), stepConfig.getOutputKey());
        }

        runningPlans.put(plan.getPlanId(), plan);
        return plan;
    }

    /**
     * 根据planId获取计划
     */
    public Plan getPlan(String planId) {
        return runningPlans.get(planId);
    }

    /**
     * 执行计划，按顺序执行每个步骤，返回最终执行完成的计划
     */
    public Mono<Plan> executePlan(String planId) {
        Plan plan = runningPlans.get(planId);
        if (plan == null) {
            return Mono.error(new IllegalArgumentException("Plan not found: " + planId));
        }

        plan.setStatus(PlanStatus.RUNNING);
        plan.setStartedAt(LocalDateTime.now());

        return executeSteps(plan)
                .then(Mono.fromCallable(() -> {
                    plan.setStatus(PlanStatus.COMPLETED);
                    plan.setCompletedAt(LocalDateTime.now());
                    return plan;
                }))
                .onErrorResume(e -> {
                    log.error("Plan execution failed", e);
                    plan.setStatus(PlanStatus.FAILED);
                    plan.setErrorMessage(e.getMessage());
                    plan.setCompletedAt(LocalDateTime.now());
                    return Mono.just(plan);
                });
    }

    /**
     * 顺序执行所有步骤
     */
    private Mono<Void> executeSteps(Plan plan) {
        Mono<Void> result = Mono.empty();
        for (Plan.Step step : plan.getSteps()) {
            result = result.then(executeStep(plan, step));
        }
        return result;
    }

    /**
     * 执行单个步骤
     */
    private Mono<Void> executeStep(Plan plan, Plan.Step step) {
        step.setStartTime(LocalDateTime.now());

        String instruction = buildInstruction(plan, step);

        return callAgent(instruction)
                .doOnSuccess(result -> {
                    step.setResult(result);
                    step.setEndTime(LocalDateTime.now());
                    plan.getContext().put(step.getOutputKey(), result);
                    plan.getContext().put("last_result", result);
                })
                .doOnError(e -> {
                    log.error("Step execution failed: {}", step.getName(), e);
                    step.setResult("Error: " + e.getMessage());
                    step.setEndTime(LocalDateTime.now());
                })
                .then();
    }

    /**
     * 构建步骤指令，替换{key}占位符为实际值
     */
    private String buildInstruction(Plan plan, Plan.Step step) {
        String instruction = step.getInstruction();
        for (Map.Entry<String, Object> entry : plan.getContext().entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (instruction.contains(placeholder)) {
                instruction = instruction.replace(placeholder, String.valueOf(entry.getValue()));
            }
        }
        return instruction;
    }

    /**
     * 调用Agent执行指令
     */
    private Mono<String> callAgent(String instruction) {
        return Mono.create(emitter -> {
            try {
                ReActAgent agent = createAgent();
                Msg userMsg = Msg.builder().textContent(instruction).build();

                agent.call(userMsg)
                        .timeout(Duration.ofSeconds(120))
                        .subscribe(
                                response -> emitter.success(response.getTextContent()),
                                error -> {
                                    log.error("Agent call failed", error);
                                    emitter.error(error);
                                });
            } catch (Exception e) {
                emitter.error(e);
            }
        });
    }

    /**
     * 创建执行步骤的Agent
     */
    private ReActAgent createAgent() {
        io.agentscope.core.tool.Toolkit toolkit = new io.agentscope.core.tool.Toolkit();
        toolkit.registerTool(fileSystemTools);

        return ReActAgent.builder()
                .name("PipelineStepAgent")
                .sysPrompt("你是一个任务执行助手。根据给定的指令完成特定任务, 并返回执行结果。")
                .model(chatModel)
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /**
     * 取消计划
     * 支持取消 PLANNING（待确认）和 RUNNING（执行中）状态的计划
     */
    public void cancelPlan(String planId) {
        Plan plan = runningPlans.get(planId);
        if (plan != null &&
                (plan.getStatus() == PlanStatus.RUNNING || plan.getStatus() == PlanStatus.PLANNING)) {
            plan.setStatus(PlanStatus.CANCELLED);
            plan.setCompletedAt(LocalDateTime.now());
        }
    }

    /**
     * 移除计划
     */
    public void removePlan(String planId) {
        runningPlans.remove(planId);
    }
}
