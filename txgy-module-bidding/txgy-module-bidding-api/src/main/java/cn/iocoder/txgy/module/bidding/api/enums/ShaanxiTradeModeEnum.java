package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 交易方式（陕西5.6）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class ShaanxiTradeModeEnum {

    /** 网络竞价 */
    public static final String CODE_1 = "1";

    /** 拍卖 */
    public static final String CODE_2 = "2";

    /** 招投标 */
    public static final String CODE_3 = "3";

    /** 协议转让（非竞价） */
    public static final String CODE_4 = "4";

    /** 动态报价 */
    public static final String CODE_5 = "5";

    /** 谈判 */
    public static final String CODE_6 = "6";

    /** 综合评议 */
    public static final String CODE_7 = "7";

    /** 其他 */
    public static final String CODE_99 = "99";

    private ShaanxiTradeModeEnum() {
    }
}