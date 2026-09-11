package cn.iocoder.txgy.module.bidding.enums;

/**
 * 风险等级（qualification_safety_permit.risk_level 的 code 取值）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§1.2（risk.level.mapping：risk_flag → risk_level）、
 * §2.2（周检 Job 按风险等级取提前量：低 14 / 一般 7 / 较大 3 / 重大 0 天，全部可配置）。
 * <p>为什么要有独立等级层：风险档位是"业务语义"（黄/红/失效），而检查频次、提前量、推送强度是"作业参数"；
 * 用映射层隔开，业务改档位定义时不必动 Job 参数，运维调检查频次时也不必理解业务档位。
 * <p>业务目标：档位与作业参数解耦，两侧可独立演进。
 */
public enum RiskLevelEnum {

    /** 低 */
    LOW,
    /** 一般 */
    MEDIUM,
    /** 较大 */
    HIGH,
    /** 重大 */
    MAJOR;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (RiskLevelEnum item : values()) {
            if (item.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 解析映射结果，非法值回落 MAJOR（宁可多检查，不可漏检）。 */
    public static RiskLevelEnum parseOrDefaultMajor(String code) {
        return isValid(code) ? valueOf(code) : MAJOR;
    }
}
