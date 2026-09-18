package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 竞价交易方式（陕西4.13）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class ShaanxiBidTradeModeEnum {

    /** 招投标 */
    public static final String CODE_1 = "1";

    /** 拍卖 */
    public static final String CODE_2 = "2";

    /** 挂牌 */
    public static final String CODE_3 = "3";

    /** 协议出让 */
    public static final String CODE_250 = "250";

    /** 申请在先 */
    public static final String CODE_260 = "260";

    /** 其他 */
    public static final String CODE_999 = "999";

    private ShaanxiBidTradeModeEnum() {
    }
}