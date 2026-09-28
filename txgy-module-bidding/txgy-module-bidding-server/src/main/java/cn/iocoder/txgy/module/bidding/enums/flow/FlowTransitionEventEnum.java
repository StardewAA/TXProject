package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 招标全流程流转事件（事件驱动状态机的动作清单）。
 *
 * <p>设计原则（用户方案 §六-1）：状态变更必须通过动作方法（如 tenderService.publish(id)），
 * 不在业务代码里直接 setStatus——动作方法内部：状态机校验 → 状态变更 → 联动（公告创建/通知/索引）→ 留痕。
 * 本枚举登记统一的动作名，供流转留痕表与操作日志使用。
 *
 * <p>{@code timeDriven=true} 为时间轴事件（投标开始/截止、公示过期），由定时任务按时间触发；
 * 配置 {@code bidding.flow.manual-advance-enabled=false}（PROD 默认）时禁止手动执行（不能用手动下一步代替时间轴），
 * 其余操作/审批事件始终允许手动。
 */
public enum FlowTransitionEventEnum {

    /** 提交审批（项目立项/招标方案） */
    SUBMIT_APPROVAL("提交审批", false),
    /** 审批通过 */
    APPROVE_PASS("审批通过", false),
    /** 审批驳回 */
    APPROVE_REJECT("审批驳回", false),
    /** 发布招标公告（PREPARING→ANNOUNCED 触发器） */
    PUBLISH_TENDER_NOTICE("发布招标公告", false),
    /** 到达投标开始时间（时间驱动 ANNOUNCED→BIDDING） */
    BIDDING_STARTED("到达投标开始时间", true),
    /** 投标截止（时间驱动 BIDDING→PENDING_OPEN） */
    BIDDING_ENDED("投标截止", true),
    /** 开标完成（PENDING_OPEN→EVALUATING） */
    BID_OPENED("开标完成", false),
    /** 评标完成（EVALUATING→PUBLICIZING，发布候选人公示） */
    EVALUATION_FINISHED("评标完成", false),
    /** 发布中标候选人公示（EVALUATING→PUBLICIZING） */
    PUBLISH_CANDIDATE_PUBLICITY("发布中标候选人公示", false),
    /** 发布中标结果公告（PUBLICIZING→AWARDED） */
    PUBLISH_RESULT_NOTICE("发布中标结果公告", false),
    /** 发出中标通知书（AWARDED→CONTRACTING） */
    ISSUE_AWARD_NOTICE("发出中标通知书", false),
    /** 合同签订并归档（CONTRACTING→COMPLETED） */
    CONTRACT_SIGNED("合同签订并归档", false),
    /** 异议成立，组织原评标委员会复核（PUBLICIZING→EVALUATING） */
    OBJECTION_RECONSIDER("异议成立组织复核", false),
    /** 终止/流标（任意非终态→TERMINATED / 标段 FAILED） */
    TERMINATE("终止/流标", false),
    /** 取消（项目/草稿取消） */
    CANCEL("取消", false),
    /** 撤回（公告已发布→已撤回） */
    WITHDRAW("撤回", false),
    /** 过期（公告到期/报名截止，时间驱动 PUBLISHED→EXPIRED） */
    EXPIRE("过期", true);

    private final String label;
    /** 是否为时间轴事件（由定时任务按时间触发，非手动操作）；时间驱动事件手动执行受 manual-advance-enabled 控制 */
    private final boolean timeDriven;

    FlowTransitionEventEnum(String label, boolean timeDriven) {
        this.label = label;
        this.timeDriven = timeDriven;
    }

    public String getLabel() {
        return label;
    }

    public boolean isTimeDriven() {
        return timeDriven;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static FlowTransitionEventEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (FlowTransitionEventEnum event : values()) {
            if (event.name().equals(code)) {
                return event;
            }
        }
        return null;
    }
}
