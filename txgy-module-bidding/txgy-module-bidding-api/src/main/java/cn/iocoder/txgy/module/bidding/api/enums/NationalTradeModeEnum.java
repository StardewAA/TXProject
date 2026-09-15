package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 交易方式代码表（国家表126）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalTradeModeEnum {

    /** 公开招标 */
    public static final String CODE_01 = "01";

    /** 邀请招标 */
    public static final String CODE_02 = "02";

    /** 竞争性谈判 */
    public static final String CODE_04 = "04";

    /** 询价采购 */
    public static final String CODE_05 = "05";

    /** 单一来源采购 */
    public static final String CODE_06 = "06";

    /** 挂牌 */
    public static final String CODE_07 = "07";

    /** 拍卖 */
    public static final String CODE_08 = "08";

    /** 协议转让 */
    public static final String CODE_09 = "09";

    /** 竞价 */
    public static final String CODE_10 = "10";

    /** 直接发包 */
    public static final String CODE_11 = "11";

    /** 竞争性磋商 */
    public static final String CODE_12 = "12";

    /** 其他 */
    public static final String CODE_99 = "99";

    private NationalTradeModeEnum() {
    }
}