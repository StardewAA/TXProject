package cn.iocoder.txgy.module.bidding.enums.flow;

/**
 * 招标全流程阶段归属（招标项目/标段的生命周期大阶段）。
 *
 * <p>阶段只做归属标注与统计，不参与流转判定；流转判定由各状态枚举的 canTransit 负责。
 */
public enum FlowPhaseEnum {

    /** 前期准备：草稿→审批→待发公告 */
    PREPARE("前期准备"),
    /** 投标阶段：公告中→投标中 */
    BID("投标阶段"),
    /** 开标评标：待开标→评标中 */
    EVALUATE("开标评标"),
    /** 定标阶段：公示中→已定标 */
    DECIDE("定标阶段"),
    /** 收尾归档：合同签订中→已归档 */
    FINISH("收尾归档"),
    /** 异常终态：已终止/流标/已取消 */
    ABNORMAL("异常终态");

    private final String label;

    FlowPhaseEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
