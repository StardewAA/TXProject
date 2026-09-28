package cn.iocoder.txgy.module.hrm.service.recruit.application.listener;

import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.txgy.module.hrm.enums.ApiConstants;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferService;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitOfferServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HRM Offer 审批的 BPM 流程监听器
 *
 * <p>BPM 与 HRM 是独立微服务（独立 JVM），Spring 事件无法跨进程，
 * 因此本类以「HTTP 接收端」的形式暴露 /rpc-api 接口，由 BPM 服务在流程结束时回调：
 * 通过 → Offer「审批通过」；不通过 → 「审批未通过」；发起人撤回 → 退回草稿。
 * 同时保留 ApplicationListener 能力，便于单体部署或同进程场景直接生效。</p>
 *
 * @author OPENLAB BS
 */
@RestController
@Validated
public class HrmRecruitOfferApprovalListener extends BpmProcessInstanceStatusEventListener {

    private static final String PREFIX = ApiConstants.PREFIX + "/recruit/offer";

    @Resource
    private HrmRecruitOfferService offerService;

    @Override
    protected String getProcessDefinitionKey() {
        return HrmRecruitOfferServiceImpl.OFFER_APPROVAL_PROCESS_KEY;
    }

    @Override
    @PostMapping(PREFIX + "/update-approval-status")
    public void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) {
        offerService.handleProcessApprovalResult(event.getId(), event.getStatus(), event.getReason(),
                event.getApproverUserId());
    }

}
