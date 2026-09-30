package cn.iocoder.txgy.module.bidding.enums;

/**
 * 验真渠道（cert_verify_history.verify_channel 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §17.5（四库一平台/部级/省级/第三方）；
 * GB/T 36906—2018 接入方为对应电子证照共享服务。
 */
public enum CertVerifyChannelEnum {
    /** 四库一平台（住建行业数据共享平台） */
    FOUR_PLATFORM,
    /** 部级共享服务 */
    MINISTRY,
    /** 省级共享服务 */
    PROVINCE,
    /** 第三方核验通道 */
    THIRD_PARTY
}
