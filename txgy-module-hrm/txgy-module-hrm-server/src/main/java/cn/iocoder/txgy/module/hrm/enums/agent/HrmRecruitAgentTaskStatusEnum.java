package cn.iocoder.txgy.module.hrm.enums.agent;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * HRM 招聘 Agent 任务状态。
 *
 * <p>只有 SUCCEEDED 会产出结果；FAILED 不得留下半成品结论。</p>
 */
@Getter
@AllArgsConstructor
public enum HrmRecruitAgentTaskStatusEnum {

    PENDING("PENDING", "待执行"),
    RUNNING("RUNNING", "执行中"),
    SUCCEEDED("SUCCEEDED", "已完成"),
    FAILED("FAILED", "已失败");

    private final String status;
    private final String name;

    /** 是否已经结束（结束状态不允许再次流转） */
    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }

    public static HrmRecruitAgentTaskStatusEnum valueOfStatus(String status) {
        return Arrays.stream(values())
                .filter(item -> item.status.equals(status))
                .findFirst().orElse(null);
    }
}
