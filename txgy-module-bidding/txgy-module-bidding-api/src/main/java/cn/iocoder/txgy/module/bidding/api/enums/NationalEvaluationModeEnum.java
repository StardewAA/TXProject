package cn.iocoder.txgy.module.bidding.api.enums;

/**
 * 评标方式代码表（国家表41）——代码值常量：码值与名称照录《VO 数据对象规范》第十章；
 * 如需调整，先回规范核对再改。
 */
public final class NationalEvaluationModeEnum {

    /** 电子 */
    public static final String CODE_1 = "1";

    /** 线下 */
    public static final String CODE_2 = "2";

    /** 远程异地评标 */
    public static final String CODE_3 = "3";

    /** 其他 */
    public static final String CODE_9 = "9";

    private NationalEvaluationModeEnum() {
    }
}