package cn.iocoder.txgy.module.erp.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveRespDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpInventoryDecisionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP 接收 WMS 结果")
public interface ErpWmsCallbackApi {

    String PREFIX = ApiConstants.PREFIX + "/wms-callback";

    @PostMapping(PREFIX + "/inventory-decision")
    @Operation(summary = "接收 WMS 库存判断结果")
    CommonResult<Boolean> callbackInventoryDecision(@Valid @RequestBody ErpInventoryDecisionCallbackReqDTO reqDTO);

    @PostMapping(PREFIX + "/execution-result")
    @Operation(summary = "接收 WMS 入库或出库执行结果")
    CommonResult<Boolean> callbackExecutionResult(@Valid @RequestBody ErpWmsExecutionCallbackReqDTO reqDTO);

    @PostMapping(PREFIX + "/collaboration-b")
    @Operation(summary = "技术接收 WMS 协作单 B")
    CommonResult<ErpCollaborationBReceiveRespDTO> receiveCollaborationB(
            @Valid @RequestBody ErpCollaborationBReceiveReqDTO reqDTO);

}
