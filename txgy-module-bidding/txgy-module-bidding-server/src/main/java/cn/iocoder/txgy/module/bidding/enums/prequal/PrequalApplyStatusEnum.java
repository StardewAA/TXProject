package cn.iocoder.txgy.module.bidding.enums.prequal;

/**
 * 资格预审申请状态（投标方视角）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6；《实施条例》第19条（未通过资格预审的申请人不具有投标资格）。
 * 生命周期：SUBMITTED→PASSED/FAILED；PASSED 申请人取得投标资格，FAILED 不具有投标资格。
 */
public enum PrequalApplyStatusEnum {

    /** 已提交：申请文件已提交，待审查 */
    SUBMITTED("已提交"),
    /** 通过：资格审查通过，取得投标资格 */
    PASSED("通过"),
    /** 不通过：资格审查不通过，不具有投标资格 */
    FAILED("不通过");

    private final String label;

    PrequalApplyStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static PrequalApplyStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (PrequalApplyStatusEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
