package com.agent.java.model.plan;

/**
 * 计划状态枚举
 */
public enum PlanStatus {

    /** 计划已创建，等待执行 */
    CREATED,

    /** 计划生成完毕，等待用户确认 */
    PLANNING,

    /** 计划开始执行 */
    STARTED,

    /** 计划正在执行中 */
    RUNNING,

    /** 计划执行完成 */
    COMPLETED,

    /** 计划执行失败 */
    FAILED,

    /** 计划已暂停 */
    PAUSED,

    /** 计划已取消 */
    CANCELLED
}
