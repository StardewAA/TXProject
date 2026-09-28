package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 招标项目生命周期状态（Tender，1 招标项目 → N 标段）—— 全流程最核心状态机。
 *
 * <p>12 态分阶段：前期准备（DRAFT/APPROVING/PREPARING）→ 投标阶段（ANNOUNCED/BIDDING）→
 * 开标评标（PENDING_OPEN/EVALUATING）→ 定标阶段（PUBLICIZING/AWARDED）→ 收尾（CONTRACTING/COMPLETED）
 * + 异常终态 TERMINATED（终止/流标）。
 *
 * <p>主链路：DRAFT→APPROVING→PREPARING→ANNOUNCED→BIDDING→PENDING_OPEN→EVALUATING→PUBLICIZING→AWARDED→CONTRACTING→COMPLETED；
 * 任意非终态可被 TERMINATED（招标方主动终止/全部流标）；PUBLICIZING 可回退 EVALUATING（异议成立复核）。
 */
public enum TenderStatusEnum {

    /** 草稿：招标信息编制中 */
    DRAFT("草稿", FlowPhaseEnum.PREPARE, "招标活动信息编制中"),
    /** 审批中：提交招标方案审批 */
    APPROVING("审批中", FlowPhaseEnum.PREPARE, "招标方案提交审批，等待审批"),
    /** 待发公告：审批通过，可编制并发布招标公告 */
    PREPARING("待发公告", FlowPhaseEnum.PREPARE, "审批通过，可发布招标公告"),
    /** 公告中：招标公告已发布，获取文件/报名阶段 */
    ANNOUNCED("公告中", FlowPhaseEnum.BID, "招标公告已发布，获取文件/报名"),
    /** 投标中：投标人递交投标文件 */
    BIDDING("投标中", FlowPhaseEnum.BID, "投标人递交投标文件"),
    /** 待开标：投标截止，等待组织开标 */
    PENDING_OPEN("待开标", FlowPhaseEnum.EVALUATE, "投标截止，等待开标"),
    /** 评标中：开标完成，专家评审 */
    EVALUATING("评标中", FlowPhaseEnum.EVALUATE, "开标完成，专家评审中"),
    /** 公示中：中标候选人公示期内 */
    PUBLICIZING("公示中", FlowPhaseEnum.DECIDE, "中标候选人公示中"),
    /** 已定标：公示期满无异议，确定中标人 */
    AWARDED("已定标", FlowPhaseEnum.DECIDE, "确定中标人"),
    /** 合同签订中：发出中标通知书，签订合同 */
    CONTRACTING("合同签订中", FlowPhaseEnum.FINISH, "发出中标通知书，签订合同"),
    /** 已归档：合同签订并归档，流程终结 */
    COMPLETED("已归档", FlowPhaseEnum.FINISH, "合同签订并归档"),
    /** 已终止/流标：异常终结 */
    TERMINATED("已终止/流标", FlowPhaseEnum.ABNORMAL, "终止或流标");

    private final String label;
    private final FlowPhaseEnum phase;
    private final String description;

    TenderStatusEnum(String label, FlowPhaseEnum phase, String description) {
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
     * 状态机流转校验。规则：
     * <ul>
     *   <li>任意非终态 → TERMINATED（招标方主动终止 / 全部标段流标）</li>
     *   <li>PUBLICIZING → EVALUATING（公示期异议成立，组织复核）</li>
     *   <li>APPROVING → DRAFT（审批驳回退回修改）</li>
     *   <li>其余按主链路单向推进</li>
     * </ul>
     */
    public static boolean canTransit(TenderStatusEnum from, TenderStatusEnum to) {
        if (from == null || to == null || from == to) {
            return false;
        }
        if (to == TERMINATED && from != TERMINATED && from != COMPLETED) {
            return true; // 异常终止：任意非终态（已归档除外）
        }
        switch (from) {
            case DRAFT:
                return to == APPROVING;
            case APPROVING:
                return to == PREPARING || to == DRAFT;
            case PREPARING:
                return to == ANNOUNCED;
            case ANNOUNCED:
                return to == BIDDING;
            case BIDDING:
                return to == PENDING_OPEN;
            case PENDING_OPEN:
                return to == EVALUATING;
            case EVALUATING:
                return to == PUBLICIZING;
            case PUBLICIZING:
                return to == AWARDED || to == EVALUATING;
            case AWARDED:
                return to == CONTRACTING;
            case CONTRACTING:
                return to == COMPLETED;
            default:
                // COMPLETED / TERMINATED 终态
                return false;
        }
    }

    /** 是否终态（不可再流转） */
    public boolean isTerminal() {
        return this == COMPLETED || this == TERMINATED;
    }

    /** 按 code 查找；找不到返回 null。 */
    public static TenderStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (TenderStatusEnum status : values()) {
            if (status.name().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
