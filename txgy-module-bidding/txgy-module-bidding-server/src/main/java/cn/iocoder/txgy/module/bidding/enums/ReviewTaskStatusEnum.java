package cn.iocoder.txgy.module.bidding.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审查任务状态（字典 review_task_status）。
 *
 * <p>规范出处：《后端变更请求-平台审查任务中心-2026-09-20》§3 状态机。
 * <p>为什么用枚举：状态集合是代码里的判定依据（只允许 PENDING → APPROVED/REJECTED），
 * 中文名与配色由字典下发（前端不得硬编码）。
 *
 * @author txgy
 */
@Getter
@AllArgsConstructor
public enum ReviewTaskStatusEnum {

    PENDING("待审查"),
    APPROVED("已通过"),
    REJECTED("已驳回");

    private final String label;

}
