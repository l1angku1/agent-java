package com.agent.java.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.agent.java.model.plan.Step;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 依赖分析器，分析步骤依赖关系，构建可并行执行的步骤组
 */
@Slf4j
@Component
public class DependencyAnalyzer {

    /**
     * 步骤组，表示一组可并行执行的步骤
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class StepGroup {
        /** 步骤组中的步骤列表 */
        private List<Step> steps;
        /** 是否可并行执行 */
        private boolean parallel;
    }

    /**
     * 分析步骤依赖关系，返回可执行的步骤组
     *
     * @param steps 步骤列表
     * @return 步骤组列表，按执行顺序排列
     */
    public List<StepGroup> analyze(List<Step> steps) {
        List<StepGroup> groups = new ArrayList<>();
        Set<String> completedKeys = new HashSet<>();
        List<Step> remainingSteps = new ArrayList<>(steps);

        while (!remainingSteps.isEmpty()) {
            List<Step> readySteps = new ArrayList<>();

            for (Step step : new ArrayList<>(remainingSteps)) {
                if (isStepReady(step, completedKeys)) {
                    readySteps.add(step);
                }
            }

            if (readySteps.isEmpty()) {
                log.error("无法继续执行, 剩余步骤依赖未满足: {}", remainingSteps);
                throw new IllegalStateException("存在循环依赖或缺少前置步骤");
            }

            boolean canParallel = readySteps.size() > 1;
            groups.add(new StepGroup(readySteps, canParallel));

            for (Step step : readySteps) {
                if (step.getOutputKey() != null) {
                    completedKeys.add(step.getOutputKey());
                }
                remainingSteps.remove(step);
            }
        }

        return groups;
    }

    /**
     * 判断步骤是否可执行（所有依赖都已满足）
     */
    private boolean isStepReady(Step step, Set<String> completedKeys) {
        List<String> dependsOn = step.getDependsOn();
        if (dependsOn == null || dependsOn.isEmpty()) {
            return true;
        }
        return completedKeys.containsAll(dependsOn);
    }
}
