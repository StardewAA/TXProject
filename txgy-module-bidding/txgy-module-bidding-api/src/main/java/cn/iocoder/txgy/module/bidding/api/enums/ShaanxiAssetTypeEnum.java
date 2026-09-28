package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 资产类型（实物资产类）（陕西5.6）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class ShaanxiAssetTypeEnum {

    /** 房产 */
    public static final String CODE_1 = "1";

    /** 土地使用权 */
    public static final String CODE_2 = "2";

    /** 交通运输工具 */
    public static final String CODE_3 = "3";

    /** 设备 */
    public static final String CODE_4 = "4";

    /** 在建工程 */
    public static final String CODE_5 = "5";

    /** 其他资产 */
    public static final String CODE_99 = "99";

    private ShaanxiAssetTypeEnum() {
    }
}