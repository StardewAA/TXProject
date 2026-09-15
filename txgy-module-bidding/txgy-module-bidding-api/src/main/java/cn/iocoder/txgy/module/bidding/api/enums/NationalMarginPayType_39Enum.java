package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 保证金缴纳方式代码表（国家表39）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalMarginPayType_39Enum {

    /** 资金现金 */
    public static final String CODE_1 = "1";

    /** 银行保函 */
    public static final String CODE_2 = "2";

    /** 担保 */
    public static final String CODE_3 = "3";

    /** 电汇 */
    public static final String CODE_4 = "4";

    /** 汇票 */
    public static final String CODE_5 = "5";

    /** 支票 */
    public static final String CODE_6 = "6";

    /** 其他 */
    public static final String CODE_9 = "9";

    private NationalMarginPayType_39Enum() {
    }
}