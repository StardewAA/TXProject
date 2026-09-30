package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 时间驱动调度记录状态（bidding_flow_timer.status）。
 *
 * <p>幂等设计：调度器到点执行，成功→DONE；若手动已提前流转（状态不匹配）→SKIPPED，无副作用。
 */
public enum FlowTimerStatusEnum {

    /** 待触发（已注册未到期） */
    WAITING("待触发"),
    /** 已由调度器执行完成 */
    DONE("已执行"),
    /** 已跳过（到点时状态已变化：手动提前流转/时间被修改） */
    SKIPPED("已跳过");

    private final String label;

    FlowTimerStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
