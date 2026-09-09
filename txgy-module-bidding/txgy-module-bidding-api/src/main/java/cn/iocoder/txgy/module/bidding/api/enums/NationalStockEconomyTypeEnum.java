package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 股权类产权交易经济类型代码表（国家表84）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalStockEconomyTypeEnum {

    /** 政府部门、机构 */
    public static final String CODE_1 = "1";

    /** 国有独资公司（企业） */
    public static final String CODE_2 = "2";

    /** 国有控股企业 */
    public static final String CODE_3 = "3";

    /** 国有事业单位，国有社团等 */
    public static final String CODE_4 = "4";

    /** 国有参股企业 */
    public static final String CODE_5 = "5";

    /** 非国有企业 */
    public static final String CODE_6 = "6";

    /** 外资企业 */
    public static final String CODE_7 = "7";

    /** 国有实际控制企业 */
    public static final String CODE_8 = "8";

    /** 其他 */
    public static final String CODE_99 = "99";

    private NationalStockEconomyTypeEnum() {
    }
}