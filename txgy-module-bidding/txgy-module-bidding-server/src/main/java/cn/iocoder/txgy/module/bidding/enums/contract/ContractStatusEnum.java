package cn.iocoder.txgy.module.bidding.enums.contract;

/**
 * 合同状态（合同签署与项目完结，说明书 §11）。
 *
 * <p>SIGNED 已签（可编辑）→ ARCHIVED 已归档（只读锁定，日志只追加不覆盖，存档 ≥15 年）。
 * 归档后禁止修改（归档锁）；保存期限 = 归档时间 + 15 年（自动计算）。
 */
public enum ContractStatusEnum {

    /** 已签（可编辑，登记时校验履约保证金 10% 与签约 30 日） */
    SIGNED("已签"),
    /** 已归档（只读锁定；归档后禁止修改，存档 ≥15 年） */
    ARCHIVED("已归档");

    private final String label;

    ContractStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 反查；找不到返回 null。 */
    public static ContractStatusEnum of(String code) {
        if (code == null) {
            return null;
        }
        for (ContractStatusEnum item : values()) {
            if (item.name().equals(code)) {
                return item;
            }
        }
        return null;
    }

    /** 是否终态（归档后只读，不可再流转/修改）。 */
    public boolean isTerminal() {
        return this == ARCHIVED;
    }
}
