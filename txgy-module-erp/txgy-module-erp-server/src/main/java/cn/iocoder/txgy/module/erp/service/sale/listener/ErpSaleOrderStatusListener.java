package cn.iocoder.txgy.module.erp.service.sale.listener;

import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEventListener;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import cn.iocoder.txgy.module.erp.service.sale.ErpSaleOrderService;
import cn.iocoder.txgy.module.erp.service.sale.ErpSaleOrderServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 接收 BPM 服务回调并同步销售订单状态。 */
@RestController
@Validated
public class ErpSaleOrderStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PREFIX = ApiConstants.PREFIX + "/sale-order";

    @Resource
    private ErpSaleOrderService saleOrderService;

    @Override
    protected String getProcessDefinitionKey() {
        return ErpSaleOrderServiceImpl.BPM_PROCESS_DEFINITION_KEY;
    }

    @Override
    @PostMapping(PREFIX + "/update-audit-status")
    protected void onEvent(@RequestBody BpmProcessInstanceStatusEvent event) {
        saleOrderService.updateSaleOrderAuditStatus(Long.parseLong(event.getBusinessKey()), event.getStatus());
    }

}
