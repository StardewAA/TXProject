package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 项目生命周期状态（Project，1 项目 → N 招标项目）。
 *
 * <p>立项层面状态：草稿 → 审批 → 已立项 → 完结/取消。空白状态用 DRAFT 兜底。
 * 流转规则见 {@link #canTransit}；终态 COMPLETED / CANCELLED 不可再流转。
 */
public enum ProjectStatusEnum {

    /** 草稿：项目信息编制中 */
    DRAFT("草稿", "项目信息编制中"),
    /** 审批中：提交立项审批 */
    APPROVING("审批中", "提交立项审批，等待审批"),
    /** 已立项：审批通过，可创建招标项目 */
    ACTIVE("已立项", "审批通过，可创建招标项目"),
    /** 已完结：所有下属招标项目均已归档 */
    COMPLETED("已完结", "所有下属招标项目均已归档"),
    /** 已取消：项目取消 */
    CANCELLED("已取消", "项目取消");

    private final String label;
    private final String description;

    ProjectStatusEnum(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 状态机流转校验。
     *
     * <p>DRAFT→APPROVING（提交审批）；APPROVING→ACTIVE（审批通过）/CANCELLED（驳回取消）；
     * ACTIVE→COMPLETED（所有招标项目归档）/CANCELLED（取消）；终态无出口。
     */
    public static boolean canTransit(ProjectStatusEnum from, ProjectStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        switch (from) {
            case DRAFT:
                return to == APPROVING || to == CANCELLED;
            case APPROVING:
                return to == ACTIVE || to == CANCELLED;
            case ACTIVE:
                return to == COMPLETED || to == CANCELLED;
            default:
                // COMPLETED / CANCELLED 终态
                return false;
        }
    }

    /** 是否终态（不可再流转） */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static ProjectStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ProjectStatusEnum status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
