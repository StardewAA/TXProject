package cn.iocoder.txgy.module.bidding.service.certificate;

/**
 * 通知渠道发送器（供 cert_notify_outbox 发送任务调用；实现类按渠道对接外部通知网关）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12（可靠投递闭环 PENDING→SENDING→
 * SUCCESS/FAILED/DEAD）；《招投标模块开发规范》§6（外部集成以接口隔离，未开通渠道以日志落点闭环）。
 */
public interface CertNotifyChannelSender {

    /**
     * 发送一条通知。抛异常视为发送失败（由发件箱任务登记 FAILED 并按退避重试）。
     */
    void send(String channel, String receiver, String payloadJson) throws Exception;
}
