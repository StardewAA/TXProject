package cn.iocoder.txgy.module.bidding.enums;

/**
 * 通知渠道（cert_notify_outbox.channel 与 config.notify_channels 元素取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §九（notify_channels：EMAIL/SMS/IN_APP/DINGTALK）＋
 * §十一 表12（channel 另含 WEBHOOK）。
 */
public enum CertNotifyChannelEnum {
    EMAIL, SMS, IN_APP, DINGTALK, WEBHOOK
}
