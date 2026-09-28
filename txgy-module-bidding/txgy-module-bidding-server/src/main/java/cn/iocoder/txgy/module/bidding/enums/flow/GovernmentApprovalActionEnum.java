package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 政府审批动作（预留接口）。
 */
public enum GovernmentApprovalActionEnum {

    /** 审批通过 */
    APPROVE("通过"),
    /** 审批驳回 */
    REJECT("驳回");

    private final String label;

    GovernmentApprovalActionEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static GovernmentApprovalActionEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (GovernmentApprovalActionEnum action : values()) {
            if (action.name().equals(code)) {
                return action;
            }
        }
        return null;
    }
}
