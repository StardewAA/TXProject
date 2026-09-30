package cn.iocoder.txgy.module.bidding.enums.contract;

/**
 * 合同一致性比对结果（合同价 vs 中标结果公示金额，说明书 §11.3 第 2 条/§11.5）。
 *
 * <p>save 登记合同（或合同价变更）时自动比对落库；前端据此对不一致合同标红提示，
 * 由招标人确认（标红提示非硬拦截）。
 */
public enum ContractCompareResultEnum {

    /** 与中标结果金额一致 */
    MATCH("一致"),
    /** 与中标结果金额不一致（标红提示） */
    MISMATCH("不一致"),
    /** 无中标结果基准（未发布中标结果公示 / 公示缺金额 / 金额非数字），不参与标红 */
    NO_BASELINE("无基准");

    private final String label;

    ContractCompareResultEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
