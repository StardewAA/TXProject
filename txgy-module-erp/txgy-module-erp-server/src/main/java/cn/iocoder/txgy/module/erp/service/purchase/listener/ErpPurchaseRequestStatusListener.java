package cn.iocoder.txgy.module.erp.service.purchase.listener;

import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseRequestService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseRequestServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
public class ErpPurchaseRequestStatusListener extends BpmProcessInstanceStatusEventListener {
    private static final String PREFIX = ApiConstants.PREFIX + "/purchase-request";
    @Resource private ErpPurchaseRequestService requestService;
    @Override protected String getProcessDefinitionKey() { return ErpPurchaseRequestServiceImpl.BPM_PROCESS_DEFINITION_KEY; }
    @Override @PostMapping(PREFIX + "/update-audit-status")
    protected void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) {
        requestService.updatePurchaseRequestAuditStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }
}
