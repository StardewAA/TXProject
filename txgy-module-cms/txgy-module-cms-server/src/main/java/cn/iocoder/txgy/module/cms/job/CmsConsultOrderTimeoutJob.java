package cn.iocoder.txgy.module.cms.job;

import cn.iocoder.txgy.module.cms.service.consult.CmsExpertConsultService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 咨询订单超时扫描；退款动作由支付整合层消费 REFUNDING 状态完成。 */
@Component
public class CmsConsultOrderTimeoutJob {
    private final CmsExpertConsultService service;
    public CmsConsultOrderTimeoutJob(CmsExpertConsultService service) { this.service = service; }

    @Scheduled(cron = "${txgy.cms.consult.timeout-cron:0 */5 * * * ?}")
    public void execute() { service.expireOrders(); }
}
