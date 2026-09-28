package cn.iocoder.txgy.module.wms.api.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/** BPM 服务回写 WMS 入库审批结果的内部接口，沿用 RPC 登录与租户过滤器。 */
@RestController
@RequestMapping("/rpc-api/wms/receipt-order")
@Validated
public class WmsReceiptOrderApiImpl {

    @Resource
    private WmsReceiptOrderService receiptOrderService;

    @PostMapping("/update-approval-status")
    public CommonResult<Boolean> updateApprovalStatus(@Valid @RequestBody BpmProcessInstanceStatusEvent event) {
        Assert.isTrue("wms_receipt_approval".equals(event.getProcessDefinitionKey()), "入库审批流程标识不匹配");
        Assert.hasText(event.getId(), "流程实例编号不能为空");
        Assert.hasText(event.getBusinessKey(), "入库单编号不能为空");
        receiptOrderService.updateReceiptOrderApprovalStatus(Long.parseLong(event.getBusinessKey()),
                event.getId(), event.getStatus(), event.getReason());
        return success(true);
    }
}
