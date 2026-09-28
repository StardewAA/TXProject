package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 标段（包）生命周期状态（BidSection，最小投标单元）。
 *
 * <p>与招标项目大体同步但<b>独立设置</b>：支持部分标段流标（FAILED）、部分标段正常定标的场景。
 * 链路：DRAFT→BIDDING→EVALUATING→AWARDED→COMPLETED；BIDDING/EVALUATING 可流标 FAILED。
 */
public enum BidSectionStatusEnum {

    /** 草稿：标段信息编制中 */
    DRAFT("草稿", FlowPhaseEnum.PREPARE, "标段信息编制中"),
    /** 招标中：涵盖公告、投标、开标阶段 */
    BIDDING("招标中", FlowPhaseEnum.BID, "公告/投标/开标阶段"),
    /** 评标中：专家正在评审该标段 */
    EVALUATING("评标中", FlowPhaseEnum.EVALUATE, "专家评审中"),
    /** 已定标：已确定中标人 */
    AWARDED("已定标", FlowPhaseEnum.DECIDE, "已确定中标人"),
    /** 流标：投标不足 3 家或全部废标 */
    FAILED("流标", FlowPhaseEnum.ABNORMAL, "投标不足3家或全部废标"),
    /** 已完结：合同签订并归档 */
    COMPLETED("已完结", FlowPhaseEnum.FINISH, "合同签订并归档");

    private final String label;
    private final FlowPhaseEnum phase;
    private final String description;

    BidSectionStatusEnum(String label, FlowPhaseEnum phase, String description) {
        this.label = label;
        this.phase = phase;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public FlowPhaseEnum getPhase() {
        return phase;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 状态机流转校验：DRAFT→BIDDING（随招标公告发布进入招标）；
     * BIDDING→EVALUATING（开标后）/FAILED（不足3家或全废标）；
     * EVALUATING→AWARDED（定标）/FAILED；AWARDED→COMPLETED（合同签订）。终态无出口。
     */
    public static boolean canTransit(BidSectionStatusEnum from, BidSectionStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        switch (from) {
            case DRAFT:
                return to == BIDDING;
            case BIDDING:
                return to == EVALUATING || to == FAILED;
            case EVALUATING:
                return to == AWARDED || to == FAILED;
            case AWARDED:
                return to == COMPLETED;
            default:
                // FAILED / COMPLETED 终态
                return false;
        }
    }

    /** 是否终态（不可再流转） */
    public boolean isTerminal() {
        return this == FAILED || this == COMPLETED;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static BidSectionStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (BidSectionStatusEnum status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
