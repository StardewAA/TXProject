package cn.iocoder.txgy.module.bidding.enums;

/**
 * 备案受理状态（BID-ENT-REG-01 提交 → PENDING；监管 BID-SUP-FILE-01 回推 → ACCEPTED/REJECTED）。
 */
public enum FilingStatusEnum {

    /** 待监管受理 */
    PENDING,
    /** 受理通过 */
    ACCEPTED,
    /** 受理驳回 */
    REJECTED;

    /** 回推时允许的终态 */
    public static boolean isReceivable(String status) {
        return ACCEPTED.name().equals(status) || REJECTED.name().equals(status);
    }
}
