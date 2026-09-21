package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.trade.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = ApiConstants.NAME, contextId = "tradeWmsCallbackApi")
@Tag(name = "RPC 服务 - 商城接收 WMS 执行结果")
public interface TradeWmsCallbackApi {

    String PREFIX = ApiConstants.PREFIX + "/wms-callback";

    @PostMapping(PREFIX + "/execution-result")
    @Operation(summary = "接收 WMS 商城出库成功结果")
    CommonResult<Boolean> callbackExecutionResult(
            @Valid @RequestBody TradeWmsExecutionCallbackReqDTO reqDTO);
}
