package com.agent.java.plan;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Component;

import com.agent.java.model.plan.Plan;
import com.agent.java.model.plan.PlanStatus;
import com.agent.java.model.plan.Step;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.Toolkit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline计划执行器
 * 支持：
 * - 依赖分析与并行执行
 * - 失败重试与超时控制
 * - 灵活的失败策略
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelinePlanExecutor {

    private final OpenAIChatModel chatModel;
    private final DependencyAnalyzer dependencyAnalyzer;

    /** 正在运行的计划集合 */
    private final Map<String, Plan> runningPlans = new ConcurrentHashMap<>();

    /**
     * 保存计划（注册到执行器）
     */
    public Plan createPlan(Plan plan) {
        // 初始化必要字段
        if (plan.getPlanId() == null) {
            plan.setPlanId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (plan.getStatus() == null) {
            plan.setStatus(PlanStatus.CREATED);
        }
        if (plan.getContext() == null) {
            plan.setContext(new HashMap<>());
        }
        if (plan.getCreatedAt() == null) {
            plan.setCreatedAt(LocalDateTime.now());
        }
        if (plan.getSteps() == null) {
            plan.setSteps(new ArrayList<>());
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
    public Plan executePlan(String planId) {
        Plan plan = runningPlans.get(planId);
        if (plan == null) {
            throw new IllegalArgumentException("Plan not found: " + planId);
        }

        plan.setStatus(PlanStatus.RUNNING);
        plan.setStartedAt(LocalDateTime.now());

        try {
            List<DependencyAnalyzer.StepGroup> groups = dependencyAnalyzer.analyze(plan.getSteps());
            log.info("计划 {} 分析完成, 共 {} 个执行组", planId, groups.size());

            for (int i = 0; i < groups.size(); i++) {
                DependencyAnalyzer.StepGroup group = groups.get(i);
                log.info("执行第 {}/{} 组, 并行: {}, 步骤数: {}",
                        i + 1, groups.size(), group.isParallel(), group.getSteps().size());

                executeStepGroup(plan, group);
            }

            plan.setStatus(PlanStatus.COMPLETED);
            plan.setCompletedAt(LocalDateTime.now());
        } catch (Exception e) {
            log.error("Plan execution failed", e);
            plan.setStatus(PlanStatus.FAILED);
            plan.setErrorMessage(e.getMessage());
            plan.setCompletedAt(LocalDateTime.now());
        }

        return plan;
    }

    /**
     * 执行步骤组
     */
    private void executeStepGroup(Plan plan, DependencyAnalyzer.StepGroup group) {
        if (group.isParallel()) {
            executeParallel(plan, group.getSteps());
        } else {
            for (Step step : group.getSteps()) {
                executeSingleStep(plan, step);
            }
        }
    }

    /**
     * 并行执行步骤组（使用CompletableFuture）
     */
    private void executeParallel(Plan plan, List<Step> steps) {
        List<CompletableFuture<Void>> futures = steps.stream()
                .map(step -> CompletableFuture.runAsync(() -> executeSingleStep(plan, step)))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * 执行单个步骤（含重试、超时）
     */
    private void executeSingleStep(Plan plan, Step step) {
        step.setStartTime(LocalDateTime.now());

        String instruction = buildInstruction(plan, step);
        int maxRetries = step.getMaxRetries();
        long timeoutSeconds = step.getTimeoutSeconds();
        String failStrategy = step.getFailStrategy();

        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                log.info("步骤 {} 重试 {}/{}", step.getName(), attempt, maxRetries);
            }

            try {
                String result = executeStepWithTimeout(instruction, timeoutSeconds);
                step.setResult(result);
                step.setEndTime(LocalDateTime.now());

                if (step.getOutputKey() != null) {
                    plan.getContext().put(step.getOutputKey(), result);
                }
                plan.getContext().put("last_result", result);

                log.info("步骤 {} 执行成功, 耗时 {}s", step.getName(), step.getDurationSeconds());
                return;

            } catch (TimeoutException e) {
                log.error("步骤 {} 执行超时 ({}/{}s)", step.getName(), attempt + 1, maxRetries);
                lastException = e;
            } catch (Exception e) {
                log.error("步骤 {} 执行失败 ({}/{})", step.getName(), attempt + 1, maxRetries + 1, e);
                lastException = e;
            }
        }

        handleStepFailure(plan, step, lastException, failStrategy);
    }

    /**
     * 带超时执行步骤（使用CompletableFuture）
     */
    private String executeStepWithTimeout(String instruction, long timeoutSeconds)
            throws Exception {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callAgent(instruction));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * 处理步骤失败
     */
    private void handleStepFailure(Plan plan, Step step, Exception e, String failStrategy) {
        step.setResult("ERROR: " + e.getMessage());
        step.setEndTime(LocalDateTime.now());

        switch (failStrategy != null ? failStrategy : "abort") {
            case "skip":
                log.warn("步骤 {} 执行失败, 按策略跳过", step.getName());
                break;
            case "abort":
                log.error("步骤 {} 执行失败, 按策略中止计划", step.getName());
                throw new RuntimeException("Step failed: " + step.getName(), e);
            case "fallback":
                log.warn("步骤 {} 执行失败, fallback 策略暂未实现, 按 skip 处理", step.getName());
                break;
            default:
                throw new RuntimeException("Step failed: " + step.getName(), e);
        }
    }

    /**
     * 构建步骤指令，替换{key}占位符为实际值
     */
    private String buildInstruction(Plan plan, Step step) {
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
    private String callAgent(String instruction) {
        ReActAgent agent = createAgent();
        Msg userMsg = Msg.builder().textContent(instruction).build();
        log.info("AI Service request content:\n{}", userMsg.getTextContent());

        try {
            Msg response = agent.call(userMsg).block(Duration.ofSeconds(120));
            if (response == null) {
                throw new RuntimeException("Agent returned null response");
            }

            log.info("AI Service response content:\n{}", response.getTextContent());
            return response.getTextContent();
        } catch (Exception e) {
            log.error("Agent call failed", e);
            throw new RuntimeException("Agent call failed: " + e.getMessage(), e);
        }
    }

    /**
     * 创建执行步骤的Agent
     */
    private ReActAgent createAgent() {
        Toolkit toolkit = new Toolkit();

        return ReActAgent.builder()
                .name("PipelineStepAgent")
                .sysPrompt("你是一个任务执行助手。根据给定的指令直接完成任务并返回最终结果。" +
                        "如果是数学计算问题，直接给出计算结果。" +
                        "不要询问用户任何问题，直接执行并返回答案。")
                .model(chatModel)
                .toolkit(toolkit)
                .maxIters(5)
                .build();
    }

    /**
     * 取消计划
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
