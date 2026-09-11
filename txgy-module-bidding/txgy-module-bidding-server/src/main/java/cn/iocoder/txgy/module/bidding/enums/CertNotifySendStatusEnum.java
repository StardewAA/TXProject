package cn.iocoder.txgy.module.bidding.enums;

/**
 * 通知发送状态（cert_notify_outbox.send_status / records.notify_status 的 code 取值）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12：
 * PENDING/SENDING/SUCCESS/FAILED/DEAD（DEAD=死信转人工+告警）；与业务同事务写入，通知不丢不重。
 */
public enum CertNotifySendStatusEnum {
    PENDING, SENDING, SUCCESS, FAILED, DEAD
}
