package cn.iocoder.txgy.module.erp.api.sale;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.erp.api.sale.dto.ErpSaleOrderRespDTO;
import cn.iocoder.txgy.module.erp.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 销售订单 RPC API 接口
 *
 * 供 MES 等下游模块查询销售订单基础信息（只读），不涉及任何业务状态变更。
 *
 * @author OPENLAB BS
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP 销售订单")
public interface ErpSaleOrderApi {

    String PREFIX = ApiConstants.PREFIX + "/sale-order";

    @GetMapping(PREFIX + "/get")
    @Operation(summary = "通过 ID 查询销售订单")
    @Parameter(name = "id", description = "销售订单编号", example = "1", required = true)
    CommonResult<ErpSaleOrderRespDTO> getSaleOrder(@RequestParam("id") Long id);

    @GetMapping(PREFIX + "/get-by-no")
    @Operation(summary = "通过订单号查询销售订单")
    @Parameter(name = "no", description = "销售订单号", example = "XSDD20260910001", required = true)
    CommonResult<ErpSaleOrderRespDTO> getSaleOrderByNo(@RequestParam("no") String no);

}
