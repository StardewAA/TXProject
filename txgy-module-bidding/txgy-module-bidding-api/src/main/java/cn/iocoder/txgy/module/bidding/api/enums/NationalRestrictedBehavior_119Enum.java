package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 限制的行为代码表（国家表119）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalRestrictedBehavior_119Enum {

    /** 禁止参加招标代理活动 */
    public static final String CODE_1 = "1";

    /** 禁止参加投标活动 */
    public static final String CODE_2 = "2";

    /** 禁止参加评标、评审活动 */
    public static final String CODE_3 = "3";

    /** 其他 */
    public static final String CODE_9 = "9";

    private NationalRestrictedBehavior_119Enum() {
    }
}