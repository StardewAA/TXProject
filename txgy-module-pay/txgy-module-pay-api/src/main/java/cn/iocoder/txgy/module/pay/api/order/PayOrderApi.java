package cn.iocoder.txgy.module.pay.api.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.txgy.module.pay.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.PermitAll;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 支付单")
public interface PayOrderApi {

    String PREFIX = ApiConstants.PREFIX + "/order";

    @PostMapping(PREFIX + "/create")
    @Operation(summary = "创建支付单")
    CommonResult<Long> createOrder(@Valid @RequestBody PayOrderCreateReqDTO reqDTO);

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "获得支付单")
    @Parameter(name = "id", description = "支付单编号", example = "1", required = true)
    @PermitAll
    CommonResult<PayOrderRespDTO> getOrder(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/get-tenant-id")
    @Operation(summary = "获得支付单所属租户（内部跨租户业务路由）")
    @Parameter(name = "id", description = "支付单编号", required = true)
    @PermitAll
    CommonResult<Long> getOrderTenantId(@RequestParam("id") Long id);

    @PutMapping(PREFIX + "/expire")
    @Operation(summary = "核实并关闭指定已到期支付单（内部业务调用）")
    CommonResult<Boolean> expireOrder(@RequestParam("id") Long id,
                                      @RequestParam("appKey") String appKey,
                                      @RequestParam("merchantOrderId") String merchantOrderId);

    @PutMapping(PREFIX + "/close")
    @Operation(summary = "核实并关闭业务取消的支付单（内部调用，不强关第三方渠道）")
    CommonResult<Boolean> closeOrder(@RequestParam("id") Long id,
                                     @RequestParam("appKey") String appKey,
                                     @RequestParam("merchantOrderId") String merchantOrderId);

    @PutMapping(PREFIX + "/update-price")
    @Operation(summary = "更新支付订单价格")
    @Parameters({
            @Parameter(name = "id", description = "支付单编号", example = "1", required = true),
            @Parameter(name = "payPrice", description = "支付单价格", example = "100", required = true)
    })
    CommonResult<Boolean> updatePayOrderPrice(@RequestParam("id") Long id,
                                              @RequestParam("payPrice") Integer payPrice);

}
