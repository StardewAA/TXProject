package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 公告性质代码表（国家表32）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalNoticeNatureEnum {

    /** 正常公告 */
    public static final String CODE_1 = "1";

    /** 更正公告 */
    public static final String CODE_2 = "2";

    /** 重发公告 */
    public static final String CODE_3 = "3";

    /** 其他 */
    public static final String CODE_9 = "9";

    private NationalNoticeNatureEnum() {
    }
}