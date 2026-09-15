package cn.iocoder.txgy.module.bidding.enums;

/**
 * 规则配置值类型（bidding_rule_config.value_type 的 code 取值）。
 *
 * <p>规范出处：《电子资质七大模块技术方案.md》§8.1（value_type 决定前端解释方式与发布校验口径）。
 * <p>为什么这样写：config_value 统一存 JSON（标量也写 JSON），若不给类型标签，
 * 前端拿到 "60" 无法区分"字符串 60"与"数字 60"，发布时也无法校验边界（legalFloor 只对数值有意义）。
 * <p>业务目标：类型显式化，前端零猜测；发布时按类型强校验，避免把阈值配成字符串导致比较失效。
 */
public enum RuleConfigValueTypeEnum {

    /** 整数（天数/秒数/条数等阈值） */
    INT,
    /** 小数（相似度阈值等） */
    DECIMAL,
    /** 布尔开关 */
    BOOL,
    /** 字符串（枚举 code、模型标识等） */
    STRING,
    /** 数组/对象（多级阈值、映射矩阵） */
    JSON;

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (RuleConfigValueTypeEnum item : values()) {
            if (item.name().equals(code)) {
                return true;
            }
        }
        return false;
    }

    /** 是否为数值型（数值型才受 legal_floor 的 min/max 约束）。 */
    public boolean isNumeric() {
        return this == INT || this == DECIMAL;
    }
}
