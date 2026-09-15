package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 预计评标耗时（陕西1）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class ShaanxiEvalDurationEnum {

    /** 半天 */
    public static final String CODE_1 = "1";

    /** 半天或一天 */
    public static final String CODE_2 = "2";

    /** 一天 */
    public static final String CODE_3 = "3";

    /** 一天或两天 */
    public static final String CODE_4 = "4";

    /** 两天 */
    public static final String CODE_5 = "5";

    /** 两天以上 */
    public static final String CODE_6 = "6";

    private ShaanxiEvalDurationEnum() {
    }
}