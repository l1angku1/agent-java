package com.agent.java.controller;

import com.agent.java.model.plan.Plan;
import com.agent.java.model.plan.PlanRequest;
import com.agent.java.plan.PipelinePlanExecutor;
import com.agent.java.plan.PlanGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 计划管理接口
 * 提供计划的创建、执行、查询等REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    @Autowired
    private PipelinePlanExecutor planExecutor;

    @Autowired
    private PlanGenerator planGenerator;

    /**
     * 创建计划（手动指定步骤）
     */
    @PostMapping("/create")
    public Map<String, Object> createPlan(@RequestBody PlanRequest request) {
        try {
            Plan plan = planExecutor.createPlan(request);
            return Map.of(
                    "success", true,
                    "planId", plan.getPlanId(),
                    "message", "计划创建成功",
                    "plan", plan
            );
        } catch (Exception e) {
            log.error("创建计划失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 执行计划（同步，等待执行完成）
     */
    @PostMapping("/execute/{planId}")
    public Map<String, Object> executePlan(@PathVariable String planId) {
        try {
            Plan plan = planExecutor.getPlan(planId);
            if (plan == null) {
                return Map.of("success", false, "error", "计划不存在: " + planId);
            }

            Plan executedPlan = planExecutor.executePlan(planId).block();
            return Map.of(
                    "success", true,
                    "message", "计划执行完成",
                    "plan", executedPlan
            );
        } catch (Exception e) {
            log.error("执行计划失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 执行计划（异步，立即返回）
     */
    @PostMapping("/execute-async/{planId}")
    public Map<String, Object> executePlanAsync(@PathVariable String planId) {
        try {
            Plan plan = planExecutor.getPlan(planId);
            if (plan == null) {
                return Map.of("success", false, "error", "计划不存在: " + planId);
            }

            planExecutor.executePlan(planId).subscribe(
                    result -> log.info("计划执行完成: {}", planId),
                    error -> log.error("计划执行失败: {}", planId, error)
            );

            return Map.of(
                    "success", true,
                    "message", "计划开始异步执行",
                    "planId", planId
            );
        } catch (Exception e) {
            log.error("异步执行计划失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 获取计划详情
     */
    @GetMapping("/get/{planId}")
    public Map<String, Object> getPlan(@PathVariable String planId) {
        Plan plan = planExecutor.getPlan(planId);
        if (plan == null) {
            return Map.of("success", false, "error", "计划不存在: " + planId);
        }
        return Map.of("success", true, "plan", plan);
    }

    /**
     * 获取计划步骤列表
     */
    @GetMapping("/steps/{planId}")
    public Map<String, Object> getPlanSteps(@PathVariable String planId) {
        Plan plan = planExecutor.getPlan(planId);
        if (plan == null) {
            return Map.of("success", false, "error", "计划不存在: " + planId);
        }
        return Map.of("success", true, "steps", plan.getSteps());
    }

    /**
     * 取消计划
     */
    @PostMapping("/cancel/{planId}")
    public Map<String, Object> cancelPlan(@PathVariable String planId) {
        try {
            planExecutor.cancelPlan(planId);
            return Map.of("success", true, "message", "计划已取消");
        } catch (Exception e) {
            log.error("取消计划失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 删除计划
     */
    @DeleteMapping("/remove/{planId}")
    public Map<String, Object> removePlan(@PathVariable String planId) {
        try {
            planExecutor.removePlan(planId);
            return Map.of("success", true, "message", "计划已删除");
        } catch (Exception e) {
            log.error("删除计划失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 全自动：用户输入 -> LLM拆解 -> 执行 -> 返回结果
     */
    @PostMapping("/auto")
    public Map<String, Object> autoPlanAndExecute(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("request");
            if (userRequest == null || userRequest.isBlank()) {
                return Map.of("success", false, "error", "请求内容不能为空");
            }

            log.info("开始自动生成计划: {}", userRequest);
            PlanRequest planRequest = planGenerator.generatePlan(userRequest);
            log.info("计划生成成功: {}", planRequest.getName());

            Plan plan = planExecutor.createPlan(planRequest);
            String planId = plan.getPlanId();

            log.info("开始执行计划: {}", planId);
            Plan executedPlan = planExecutor.executePlan(planId).block();

            return Map.of(
                    "success", true,
                    "message", "自动计划生成并执行完成",
                    "plan", executedPlan,
                    "generatedPlan", planRequest
            );
        } catch (Exception e) {
            log.error("自动计划生成执行失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 只生成计划，不执行（用于预览）
     */
    @PostMapping("/generate")
    public Map<String, Object> generatePlanOnly(@RequestBody Map<String, String> request) {
        try {
            String userRequest = request.get("request");
            if (userRequest == null || userRequest.isBlank()) {
                return Map.of("success", false, "error", "请求内容不能为空");
            }

            log.info("生成计划: {}", userRequest);
            PlanRequest planRequest = planGenerator.generatePlan(userRequest);
            Plan plan = planExecutor.createPlan(planRequest);

            return Map.of(
                    "success", true,
                    "message", "计划生成成功",
                    "plan", plan,
                    "planRequest", planRequest
            );
        } catch (Exception e) {
            log.error("计划生成失败", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
