package com.agent.java.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agent.java.model.plan.Plan;
import com.agent.java.model.plan.PlanAutoRequest;
import com.agent.java.model.plan.PlanResponse;
import com.agent.java.model.plan.PlanStatus;
import com.agent.java.model.plan.PlanWorkflowRequest;
import com.agent.java.plan.PipelinePlanExecutor;
import com.agent.java.plan.PlanGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计划管理接口
 * 提供计划的自动生成、确认执行等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/plan")
@RequiredArgsConstructor
public class PlanController {

    private final PipelinePlanExecutor planExecutor;
    private final PlanGenerator planGenerator;

    /**
     * 获取计划详情
     */
    @GetMapping("/get/{planId}")
    public PlanResponse getPlan(@PathVariable String planId) {
        Plan plan = planExecutor.getPlan(planId);
        if (plan == null) {
            return PlanResponse.builder().success(false).error("计划不存在: " + planId).build();
        }
        return PlanResponse.builder().success(true).plan(plan).build();
    }

    /**
     * 获取计划步骤列表
     */
    @GetMapping("/steps/{planId}")
    public PlanResponse getPlanSteps(@PathVariable String planId) {
        Plan plan = planExecutor.getPlan(planId);
        if (plan == null) {
            return PlanResponse.builder().success(false).error("计划不存在: " + planId).build();
        }
        return PlanResponse.builder().success(true).steps(plan.getSteps()).build();
    }

    /**
     * 删除计划
     */
    @DeleteMapping("/remove/{planId}")
    public PlanResponse removePlan(@PathVariable String planId) {
        try {
            planExecutor.removePlan(planId);
            return PlanResponse.builder().success(true).message("计划已删除").build();
        } catch (Exception e) {
            log.error("删除计划失败", e);
            return PlanResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * 全自动：用户输入 -> LLM拆解 -> 执行 -> 返回结果
     */
    @PostMapping("/auto")
    public PlanResponse autoPlanAndExecute(@RequestBody PlanAutoRequest request) {
        try {
            String validationError = request.validate();
            if (validationError != null) {
                return PlanResponse.builder().success(false).error(validationError).build();
            }

            log.info("开始自动生成计划: {}", request.getRequest());
            Plan plan = planGenerator.generatePlan(request.getRequest());
            log.info("计划生成成功: {}", plan.getName());

            String planId = plan.getPlanId();
            planExecutor.createPlan(plan);

            log.info("开始执行计划: {}", planId);
            Plan executedPlan = planExecutor.executePlan(planId);

            return PlanResponse.builder()
                    .success(true)
                    .message("自动计划生成并执行完成")
                    .plan(executedPlan)
                    .build();
        } catch (Exception e) {
            log.error("自动计划生成执行失败", e);
            return PlanResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * 只生成计划，不执行（用于预览）
     */
    @PostMapping("/generate")
    public PlanResponse generatePlanOnly(@RequestBody PlanAutoRequest request) {
        try {
            String validationError = request.validate();
            if (validationError != null) {
                return PlanResponse.builder().success(false).error(validationError).build();
            }

            log.info("生成计划: {}", request.getRequest());
            Plan plan = planGenerator.generatePlan(request.getRequest());
            planExecutor.createPlan(plan);

            return PlanResponse.builder()
                    .success(true)
                    .message("计划生成成功")
                    .plan(plan)
                    .build();
        } catch (Exception e) {
            log.error("计划生成失败", e);
            return PlanResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

    /**
     * 计划工作流接口（生成-确认-执行一体）
     * 通过 mode 参数控制流程阶段：
     * - preview: 生成计划待确认
     * - confirm: 确认并执行计划
     * - cancel: 取消计划
     * - status: 查询计划状态
     */
    @PostMapping("/workflow")
    public PlanResponse planWorkflow(@RequestBody PlanWorkflowRequest request) {
        try {
            String validationError = request.validate();
            if (validationError != null) {
                return PlanResponse.builder().success(false).error(validationError).build();
            }

            String mode = request.getMode();

            switch (mode) {
                case "preview": {
                    log.info("生成计划(预览模式): {}", request.getRequest());
                    Plan plan = planGenerator.generatePlan(request.getRequest());
                    planExecutor.createPlan(plan);
                    plan.setStatus(PlanStatus.PLANNING);

                    return PlanResponse.builder()
                            .success(true)
                            .message("计划已生成，请确认后执行")
                            .planId(plan.getPlanId())
                            .plan(plan)
                            .steps(plan.getSteps())
                            .status(PlanStatus.PLANNING.name())
                            .build();
                }

                case "confirm": {
                    String planId = request.getPlanId();
                    Plan plan = planExecutor.getPlan(planId);

                    if (plan == null) {
                        return PlanResponse.builder().success(false).error("计划不存在: " + planId).build();
                    }

                    if (plan.getStatus() != PlanStatus.PLANNING) {
                        return PlanResponse.builder().success(false).error("当前状态不允许确认执行: " + plan.getStatus()).build();
                    }

                    log.info("用户确认执行计划: {}", planId);
                    Plan executedPlan = planExecutor.executePlan(planId);

                    return PlanResponse.builder()
                            .success(true)
                            .message("计划执行完成")
                            .planId(planId)
                            .plan(executedPlan)
                            .status(executedPlan.getStatus().name())
                            .build();
                }

                case "cancel": {
                    String planId = request.getPlanId();
                    Plan plan = planExecutor.getPlan(planId);

                    if (plan == null) {
                        return PlanResponse.builder().success(false).error("计划不存在: " + planId).build();
                    }

                    if (plan.getStatus() != PlanStatus.PLANNING) {
                        return PlanResponse.builder().success(false).error("只能取消待确认状态的计划: " + plan.getStatus()).build();
                    }

                    planExecutor.cancelPlan(planId);

                    return PlanResponse.builder()
                            .success(true)
                            .message("计划已取消")
                            .planId(planId)
                            .status(PlanStatus.CANCELLED.name())
                            .build();
                }

                case "status": {
                    String planId = request.getPlanId();
                    Plan plan = planExecutor.getPlan(planId);

                    if (plan == null) {
                        return PlanResponse.builder().success(false).error("计划不存在: " + planId).build();
                    }

                    return PlanResponse.builder()
                            .success(true)
                            .planId(planId)
                            .status(plan.getStatus().name())
                            .plan(plan)
                            .build();
                }

                default:
                    return PlanResponse.builder().success(false).error("不支持的模式: " + mode).build();
            }
        } catch (Exception e) {
            log.error("计划工作流执行失败", e);
            return PlanResponse.builder().success(false).error(e.getMessage()).build();
        }
    }

}
