package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 内部决策情况（陕西5.6）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class ShaanxiInternalDecisionEnum {

    /** 股东会决议 */
    public static final String CODE_1 = "1";

    /** 董事会决议 */
    public static final String CODE_2 = "2";

    /** 总经理办公会议决议 */
    public static final String CODE_4 = "4";

    /** 其他 */
    public static final String CODE_5 = "5";

    /** 行政会议决议 */
    public static final String CODE_6 = "6";

    private ShaanxiInternalDecisionEnum() {
    }
}