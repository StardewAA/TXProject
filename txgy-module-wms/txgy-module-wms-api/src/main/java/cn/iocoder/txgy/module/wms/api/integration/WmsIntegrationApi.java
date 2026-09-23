package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - ERP/WMS 仓储集成")
public interface WmsIntegrationApi {

    String PREFIX = ApiConstants.PREFIX + "/integration";

    @PutMapping(PREFIX + "/master-data/material")
    @Operation(summary = "幂等新增或更新物料主数据")
    CommonResult<WmsIntegrationAcceptedRespDTO> upsertMaterial(@Valid @RequestBody WmsMaterialUpsertReqDTO reqDTO);

    @PutMapping(PREFIX + "/master-data/partner")
    @Operation(summary = "幂等新增或更新往来企业主数据")
    CommonResult<WmsIntegrationAcceptedRespDTO> upsertPartner(@Valid @RequestBody WmsPartnerUpsertReqDTO reqDTO);

    @PutMapping(PREFIX + "/master-data/warehouse")
    @Operation(summary = "通过 WMS 权威数据幂等新增或更新仓库")
    CommonResult<WmsIntegrationAcceptedRespDTO> upsertWarehouse(@Valid @RequestBody WmsWarehouseUpsertReqDTO reqDTO);

    @PostMapping(PREFIX + "/inventory/decision")
    @Operation(summary = "提交销售订单库存判断")
    CommonResult<WmsInventoryDecisionRespDTO> createInventoryDecision(
            @Valid @RequestBody WmsInventoryDecisionReqDTO reqDTO);

    @PostMapping(PREFIX + "/inventory/reservation/release")
    @Operation(summary = "释放销售订单未核销库存预留")
    CommonResult<WmsIntegrationAcceptedRespDTO> releaseReservation(
            @Valid @RequestBody WmsReservationReleaseReqDTO reqDTO);

    @PostMapping(PREFIX + "/receipt/purchase")
    @Operation(summary = "提交采购原料入库申请")
    CommonResult<WmsIntegrationAcceptedRespDTO> createPurchaseReceipt(@Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/receipt/production")
    @Operation(summary = "MES 完工入库申请，仅创建 WMS 草稿")
    CommonResult<WmsIntegrationAcceptedRespDTO> createProductionReceipt(@Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/receipt/mall-opening")
    @Operation(summary = "商城新商品期初库存入库申请，仅创建 WMS 草稿")
    CommonResult<WmsIntegrationAcceptedRespDTO> createMallOpeningReceipt(
            @Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/shipment/sale")
    @Operation(summary = "ERP 销售出库申请，仅创建 WMS 草稿")
    CommonResult<WmsIntegrationAcceptedRespDTO> createSaleShipment(@Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/shipment/mall")
    @Operation(summary = "商城销售出库申请，仅创建 WMS 草稿")
    CommonResult<WmsIntegrationAcceptedRespDTO> createMallShipment(@Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/shipment/production")
    @Operation(summary = "MES 生产领料出库申请，仅创建 WMS 草稿")
    CommonResult<WmsIntegrationAcceptedRespDTO> createProductionShipment(@Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/receipt/customer-return")
    @Operation(summary = "提交客户退货入库申请")
    CommonResult<WmsIntegrationAcceptedRespDTO> createCustomerReturnReceipt(
            @Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @PostMapping(PREFIX + "/shipment/purchase-return")
    @Operation(summary = "提交采购退货出库申请")
    CommonResult<WmsIntegrationAcceptedRespDTO> createPurchaseReturnShipment(
            @Valid @RequestBody WmsOrderRequestDTO reqDTO);

    @GetMapping(PREFIX + "/request/{requestId}")
    @Operation(summary = "按幂等请求号查询处理结果")
    CommonResult<WmsIntegrationRequestRespDTO> getRequestResult(@PathVariable("requestId") String requestId);

    @GetMapping(PREFIX + "/inventory/decision/by-sale-order")
    @Operation(summary = "按 ERP 销售订单查询库存判断结果")
    CommonResult<WmsInventoryDecisionRespDTO> getInventoryDecision(
            @RequestParam("tenantId") Long tenantId, @RequestParam("saleOrderNo") String saleOrderNo);

    @GetMapping(PREFIX + "/order/execution/by-source")
    @Operation(summary = "按 ERP 来源单号查询入库或出库处理结果")
    CommonResult<WmsIntegrationRequestRespDTO> getOrderExecution(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("sourceOrderNo") String sourceOrderNo,
            @RequestParam("orderType") String orderType);

    @GetMapping(PREFIX + "/order/execution/mall")
    @Operation(summary = "按商城订单号查询出库处理结果")
    CommonResult<WmsIntegrationRequestRespDTO> getMallOrderExecution(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("shopId") Long shopId,
            @RequestParam("sourceOrderNo") String sourceOrderNo);

    @PostMapping(PREFIX + "/collaboration-order-g/confirm")
    @Operation(summary = "MES 确认协作单 G，回写生产工单号（设计 6.3.5/6.3.6）")
    CommonResult<Boolean> confirmCollaborationOrderG(@RequestParam("tenantId") Long tenantId,
            @RequestParam("sourceOrderNo") String sourceOrderNo, @RequestParam("skuCode") String skuCode,
            @RequestParam("mesWorkOrderNo") String mesWorkOrderNo);

    @PostMapping(PREFIX + "/collaboration-order-g/reject")
    @Operation(summary = "MES 驳回协作单 G，回写原因（设计 6.3.5）")
    CommonResult<Boolean> rejectCollaborationOrderG(@RequestParam("tenantId") Long tenantId,
            @RequestParam("sourceOrderNo") String sourceOrderNo, @RequestParam("skuCode") String skuCode,
            @RequestParam("reason") String reason);

}
