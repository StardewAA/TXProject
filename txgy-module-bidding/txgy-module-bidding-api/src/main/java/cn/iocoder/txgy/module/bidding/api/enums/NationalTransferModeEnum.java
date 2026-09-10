package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 出让方式代码表（国家表72）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalTransferModeEnum {

    /** 探矿权招拍挂招标出让 */
    public static final String CODE_131 = "131";

    /** 探矿权招拍挂拍卖出让 */
    public static final String CODE_132 = "132";

    /** 探矿权招拍挂挂牌出让 */
    public static final String CODE_133 = "133";

    /** 探矿权转采矿权 */
    public static final String CODE_210 = "210";

    /** 采矿权招拍挂招标出让 */
    public static final String CODE_220 = "220";

    /** 采矿权招拍挂拍卖出让 */
    public static final String CODE_230 = "230";

    /** 采矿权招拍挂挂牌出让 */
    public static final String CODE_240 = "240";

    /** 协议出让 */
    public static final String CODE_250 = "250";

    /** 申请在先 */
    public static final String CODE_260 = "260";

    /** 其他 */
    public static final String CODE_999 = "999";

    private NationalTransferModeEnum() {
    }
}