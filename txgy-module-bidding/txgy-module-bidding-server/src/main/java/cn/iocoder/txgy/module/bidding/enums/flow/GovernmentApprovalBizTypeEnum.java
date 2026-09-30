package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 政府审批对象类型（预留接口的审批点）。
 *
 * <p>对应招标全流程中需要（政府/监管）审批的四个环节：项目立项、招标方案、终止、公告审核。
 * 预留接口 GovernmentApprovalService 当前为 stub（直接返回通过），接入真实审批系统后按本类型分派。
 */
public enum GovernmentApprovalBizTypeEnum {

    /** 项目立项审批（ProjectStatusEnum.APPROVING → ACTIVE） */
    PROJECT("项目立项"),
    /** 招标方案审批（TenderStatusEnum.APPROVING → PREPARING） */
    TENDER_PROJECT("招标方案"),
    /** 终止审批（招标项目 → TERMINATED；标段 → FAILED） */
    TERMINATION("终止/流标"),
    /** 公告审核（AnnouncementStatusEnum.REVIEWING → PUBLISHED） */
    ANNOUNCEMENT("公告审核");

    private final String label;

    GovernmentApprovalBizTypeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static GovernmentApprovalBizTypeEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (GovernmentApprovalBizTypeEnum type : values()) {
            if (type.name().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
