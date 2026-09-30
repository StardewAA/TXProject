package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.trade.enums.ApiConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 支付服务在真正发起扣款前调用的商城 WMS 库存复核。 */
@FeignClient(name = ApiConstants.NAME, contextId = "tradeWmsStockApi")
public interface TradeWmsStockApi {
    String PREFIX = ApiConstants.PREFIX + "/wms-stock";

    @PostMapping(PREFIX + "/validate-payment")
    CommonResult<Boolean> validatePayment(@RequestParam("orderId") Long orderId,
                                          @RequestParam("payOrderId") Long payOrderId,
                                          @RequestParam("memberId") Long memberId);
}
