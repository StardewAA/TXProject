package cn.iocoder.txgy.module.erp.service.purchase.listener;

import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 接收 BPM 服务回调并同步采购订单状态。 */
@RestController
@Validated
public class ErpPurchaseOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PREFIX = ApiConstants.PREFIX + "/purchase-order";

    @Resource
    private ErpPurchaseOrderService purchaseOrderService;

    @Override
    protected String getProcessDefinitionKey() {
        return ErpPurchaseOrderServiceImpl.BPM_PROCESS_DEFINITION_KEY;
    }

    @Override
    @PostMapping(PREFIX + "/update-audit-status")
    protected void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) {
        purchaseOrderService.updatePurchaseOrderAuditStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }

}
