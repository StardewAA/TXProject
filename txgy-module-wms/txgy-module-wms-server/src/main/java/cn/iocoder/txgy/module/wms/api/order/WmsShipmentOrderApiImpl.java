package cn.iocoder.txgy.module.wms.api.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** BPM 服务回写 WMS 出库审批结果的内部接口。 */
@RestController
@RequestMapping("/rpc-api/wms/shipment-order")
@Validated
public class WmsShipmentOrderApiImpl {

    @Resource
    private WmsShipmentOrderService shipmentOrderService;

    @PostMapping("/update-approval-status")
    public CommonResult<Boolean> updateApprovalStatus(
            @Valid @RequestBody BpmProcessInstanceStatusEvent event) {
        shipmentOrderService.updateShipmentOrderApprovalStatus(Long.parseLong(event.getBusinessKey()),
                event.getId(), event.getStatus(), event.getReason());
        return success(true);
    }
}
