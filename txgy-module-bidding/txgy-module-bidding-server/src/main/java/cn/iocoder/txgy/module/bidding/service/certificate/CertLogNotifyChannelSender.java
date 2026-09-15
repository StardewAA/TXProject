package cn.iocoder.txgy.module.bidding.service.certificate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 日志渠道发送器（默认实现）：暂未接入真实通知网关（EMAIL/DINGTALK 等），
 * 以 INFO 日志落点闭环发件箱状态机，保证“通知不丢不重”链路可演示、可替换。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十一 表12；开发规范 §6 外部集成留接口。
 */
@Slf4j
@Component
public class CertLogNotifyChannelSender implements CertNotifyChannelSender {

    @Override
    public void send(String channel, String receiver, String payloadJson) {
        log.info("[cert-notify][渠道={} 接收方={}] 通知发送（日志落点）：{}", channel, receiver, payloadJson);
    }
}
