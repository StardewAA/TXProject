package cn.iocoder.txgy.module.hrm.job.recruit;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * HRM Offer 过期处理 Job
 *
 * <p>扫描"已发送且已超过有效期"的 Offer，批量置为「已过期」，
 * 并把对应候选人申请同步为「Offer 已过期」，避免状态更新只能靠候选人
 * 再次访问门户被动触发（L-01）。</p>
 *
 * <p>handler 名：hrmRecruitOfferExpireJob；无需参数，建议每小时执行一次。
 * 方法内部按租户维度遍历，重复执行是幂等的（已过期的 Offer 不会再被命中）。</p>
 *
 * @author OPENLAB BS
 */
@Component
public class HrmRecruitOfferExpireJob {

    @Resource
    private HrmRecruitOfferService offerService;

    @XxlJob("hrmRecruitOfferExpireJob")
    @TenantJob
    public String execute() {
        offerService.expireOverdueOffers(null);
        return "HRM Offer 过期处理：已完成扫描（过期 Offer 状态与候选人申请状态已同步）";
    }

}
