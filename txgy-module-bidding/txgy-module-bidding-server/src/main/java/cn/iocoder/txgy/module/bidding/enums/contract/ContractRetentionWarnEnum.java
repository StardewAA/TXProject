package cn.iocoder.txgy.module.bidding.enums.contract;

/**
 * 合同保存期限提醒标记（自动计算，不落人工输入）。
 *
 * <p>依据：说明书 §12.2 第 3 条「存档（15 年）等法定时限的自动计算与提醒」；《电子招标投标办法》技术规范 存档≥15 年。
 * 计算规则：归档后 retention_until = archive_time + 15 年；
 * 距到期 ≤90 天 → EXPIRING_SOON（临期提醒）；已过到期日 → EXPIRED（到期告警）。
 */
public enum ContractRetentionWarnEnum {

    /** 正常（距保存到期 > 90 天） */
    NORMAL("正常"),
    /** 临期（距保存到期 ≤ 90 天） */
    EXPIRING_SOON("临期提醒"),
    /** 已到期（超过保存到期日） */
    EXPIRED("已到期");

    private final String label;

    ContractRetentionWarnEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
