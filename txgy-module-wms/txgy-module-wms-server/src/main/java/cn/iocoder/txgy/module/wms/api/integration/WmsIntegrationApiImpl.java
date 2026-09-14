package cn.iocoder.txgy.module.wms.api.integration;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.wms.api.integration.dto.*;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.detail.WmsReceiptOrderDetailSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.item.WmsItemSkuDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.merchant.WmsMerchantDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.md.warehouse.WmsWarehouseDO;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemSkuMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.item.WmsItemMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.merchant.WmsMerchantMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.md.warehouse.WmsWarehouseMapper;
import cn.iocoder.txgy.module.wms.service.integration.WmsIntegrationRequestService;
import cn.iocoder.txgy.module.wms.service.integration.WmsMaterialIntegrationService;
import cn.iocoder.txgy.module.wms.service.integration.WmsPartnerIntegrationService;
import cn.iocoder.txgy.module.wms.service.integration.WmsWarehouseIntegrationService;
import cn.iocoder.txgy.module.wms.service.order.receipt.WmsReceiptOrderService;
import cn.iocoder.txgy.module.wms.enums.order.WmsReceiptOrderTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.ITEM_SKU_NOT_EXISTS;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.WAREHOUSE_NOT_EXISTS;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_FEATURE_NOT_ENABLED;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_TENANT_MISMATCH;

@RestController
@Validated
public class WmsIntegrationApiImpl implements WmsIntegrationApi {

    private static final String OPERATION_MATERIAL_UPSERT = "MATERIAL_UPSERT";
    private static final String OPERATION_PARTNER_UPSERT = "PARTNER_UPSERT";
    private static final String OPERATION_WAREHOUSE_UPSERT = "WAREHOUSE_UPSERT";
    private static final String OPERATION_PURCHASE_RECEIPT = "PURCHASE_RECEIPT_CREATE";

    @Resource private WmsIntegrationRequestService requestService;
    @Resource private WmsMaterialIntegrationService materialService;
    @Resource private WmsPartnerIntegrationService partnerService;
    @Resource private WmsWarehouseIntegrationService warehouseService;
    @Resource private WmsReceiptOrderService receiptOrderService;
    @Resource @org.springframework.context.annotation.Lazy
    private cn.iocoder.txgy.module.wms.service.order.shipment.WmsShipmentOrderService shipmentOrderService;
    @Resource private WmsItemSkuMapper itemSkuMapper;
    @Resource private WmsItemMapper itemMapper;
    @Resource private WmsMerchantMapper merchantMapper;
    @Resource private WmsWarehouseMapper warehouseMapper;
    @Resource private cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderDetailMapper receiptDetailMapper;
    @Resource private cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderDetailMapper shipmentDetailMapper;

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> upsertMaterial(WmsMaterialUpsertReqDTO reqDTO) {
        return execute(OPERATION_MATERIAL_UPSERT, "MATERIAL", reqDTO, () -> materialService.upsert(reqDTO));
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> upsertPartner(WmsPartnerUpsertReqDTO reqDTO) {
        return execute(OPERATION_PARTNER_UPSERT, "PARTNER", reqDTO, () -> partnerService.upsert(reqDTO));
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> upsertWarehouse(WmsWarehouseUpsertReqDTO reqDTO) {
        return execute(OPERATION_WAREHOUSE_UPSERT, "WAREHOUSE", reqDTO, () -> warehouseService.upsert(reqDTO));
    }

    @Override
    public CommonResult<WmsIntegrationRequestRespDTO> getRequestResult(String requestId) {
        TenantContextHolder.getRequiredTenantId();
        return success(requestService.getResult(requestId));
    }

    private CommonResult<WmsIntegrationAcceptedRespDTO> execute(String operationType, String aggregateType,
                                                                 WmsIntegrationBaseReqDTO request,
                                                                 Supplier<WmsIntegrationAcceptedRespDTO> action) {
        validateTenant(request.getTenantId());
        WmsIntegrationRequestRespDTO result = requestService.execute(operationType, request,
                () -> toLedgerResponse(aggregateType, action.get()));
        return success(toAcceptedResponse(result));
    }

    private void validateTenant(Long requestTenantId) {
        if (!Objects.equals(TenantContextHolder.getRequiredTenantId(), requestTenantId)) {
            throw exception(INTEGRATION_TENANT_MISMATCH);
        }
    }

    private WmsIntegrationRequestRespDTO toLedgerResponse(String aggregateType,
                                                           WmsIntegrationAcceptedRespDTO accepted) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accepted", accepted.getAccepted());
        data.put("targetOrderNo", accepted.getTargetOrderNo());
        data.put("businessStatus", accepted.getBusinessStatus());
        return new WmsIntegrationRequestRespDTO().setAggregateType(aggregateType)
                .setAggregateNo(accepted.getTargetOrderNo()).setProcessedEventVersion(accepted.getProcessedVersion())
                .setResultCode(0).setResultData(data);
    }

    private WmsIntegrationAcceptedRespDTO toAcceptedResponse(WmsIntegrationRequestRespDTO result) {
        Map<String, Object> data = result.getResultData();
        return new WmsIntegrationAcceptedRespDTO().setAccepted(data == null || data.get("accepted") == null
                        ? Boolean.TRUE : Boolean.valueOf(String.valueOf(data.get("accepted"))))
                .setRequestId(result.getRequestId()).setTargetOrderNo(result.getAggregateNo())
                .setBusinessStatus(data == null ? result.getProcessingStatus()
                        : String.valueOf(data.get("businessStatus")))
                .setProcessedVersion(result.getProcessedEventVersion());
    }

    @Override
    public CommonResult<WmsInventoryDecisionRespDTO> createInventoryDecision(WmsInventoryDecisionReqDTO reqDTO) {
        throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> releaseReservation(WmsReservationReleaseReqDTO reqDTO) {
        throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createPurchaseReceipt(WmsOrderRequestDTO reqDTO) {
        validatePurchaseReceipt(reqDTO);
        return execute(OPERATION_PURCHASE_RECEIPT, "RECEIPT", reqDTO,
                () -> toAcceptedResponse(requestService.executePurchaseReceiptBusiness(reqDTO,
                        () -> toLedgerResponse("RECEIPT", createPurchaseReceiptOrder(reqDTO)))));
    }

    private void validatePurchaseReceipt(WmsOrderRequestDTO request) {
        validateOrder(request, "ERP", "PURCHASE_RECEIPT", "RAW_MATERIAL");
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createProductionReceipt(WmsOrderRequestDTO request) {
        validateOrder(request, "MES", "PRODUCTION_RECEIPT", "FINISHED_GOODS");
        return execute("PRODUCTION_RECEIPT_CREATE", "RECEIPT", request,
                () -> toAcceptedResponse(requestService.executeOrderBusiness(request,
                        () -> toLedgerResponse("RECEIPT", createPurchaseReceiptOrder(request)))));
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createSaleShipment(WmsOrderRequestDTO request) {
        validateOrder(request, "ERP", "SALE_SHIPMENT", "FINISHED_GOODS");
        return createExternalShipment(request);
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createProductionShipment(WmsOrderRequestDTO request) {
        validateOrder(request, "MES", "PRODUCTION_SHIPMENT", "RAW_MATERIAL");
        return createExternalShipment(request);
    }

    private CommonResult<WmsIntegrationAcceptedRespDTO> createExternalShipment(WmsOrderRequestDTO request) {
        return execute(request.getOrderType() + "_CREATE", "SHIPMENT", request,
                () -> toAcceptedResponse(requestService.executeOrderBusiness(request,
                        () -> toLedgerResponse("SHIPMENT", createShipmentOrder(request)))));
    }

    private WmsIntegrationAcceptedRespDTO createShipmentOrder(WmsOrderRequestDTO request) {
        String materialType = "MES".equals(request.getSourceModule()) ? "RAW_MATERIAL" : "FINISHED_GOODS";
        var warehouse = warehouseMapper.selectByCode(request.getItems().getFirst().getWarehouseCode());
        if (warehouse == null) throw exception(WAREHOUSE_NOT_EXISTS);
        requireReceipt(materialType.equals(warehouse.getWarehouseType()), "仓库类型必须为 " + materialType);
        var merchant = request.getPartnerCode() == null || request.getPartnerCode().isBlank()
                ? null : merchantMapper.selectByCode(request.getPartnerCode());
        if (validText(request.getPartnerCode(), 64) && merchant == null) throw exception(MERCHANT_NOT_EXISTS);
        var details = request.getItems().stream().map(item -> {
            var sku = itemSkuMapper.selectByCode(item.getSkuCode());
            if (sku == null) throw exception(ITEM_SKU_NOT_EXISTS);
            var material = itemMapper.selectById(sku.getItemId());
            requireReceipt(material != null && Objects.equals(material.getCode(), item.getMaterialCode())
                    && materialType.equals(material.getMaterialType()), "SKU 不属于申请物料或物料类型不符");
            return new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.detail.WmsShipmentOrderDetailSaveReqVO()
                    .setSkuId(sku.getId()).setQuantity(item.getRequiredQuantity())
                    .setPrice(item.getPrice()).setTotalPrice(sourceAmount(item))
                    .setSourceLineNo(item.getSourceLineNo() == null ? item.getLineNo() : item.getSourceLineNo())
                    .setSourceSnapshotJson(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(item));
        }).toList();
        String no = "WMS-" + request.getSourceModule() + "-" + request.getSourceOrderNo();
        shipmentOrderService.createShipmentOrder(new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO()
                .setNo(no).setType("MES".equals(request.getSourceModule())
                        ? cn.iocoder.txgy.module.wms.enums.order.WmsShipmentOrderTypeEnum.PRODUCTION.getType()
                        : cn.iocoder.txgy.module.wms.enums.order.WmsShipmentOrderTypeEnum.SALE.getType())
                .setOrderTime(request.getOccurredAt()).setWarehouseId(warehouse.getId())
                .setMerchantId(merchant == null ? null : merchant.getId()).setBizOrderNo(request.getSourceOrderNo())
                .setSourceModule(request.getSourceModule()).setBusinessChainNo(request.getBusinessChainNo()).setDetails(details));
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setTargetOrderNo(no).setBusinessStatus("RECEIVED").setProcessedVersion(request.getEventVersion());
    }

    private void validateOrder(WmsOrderRequestDTO request, String source, String type, String materialType) {
        requireReceipt(request != null, "请求不能为空");
        validateTenant(request.getTenantId());
        requireReceipt(source.equals(request.getSourceModule()) && type.equals(request.getOrderType()),
                "此接口只接受 " + source + " " + type);
        requireReceipt(validText(request.getRequestId(), 64) && !request.getRequestId().startsWith("B:"), "requestId 无效");
        requireReceipt(validText(request.getSourceOrderNo(), 56), "来源单号不能为空且不能超过 56 字符");
        requireReceipt(validText(request.getBusinessChainNo(), 64), "业务链编号不能为空且不能超过 64 字符");
        requireReceipt(request.getOccurredAt() != null && request.getEventVersion() != null && request.getEventVersion() > 0,
                "业务时间及正整数事件版本不能为空");
        requireReceipt(request.getItems() != null && !request.getItems().isEmpty(), "明细不能为空");
        java.util.Set<Integer> lines = new java.util.HashSet<>();
        java.util.Set<Integer> sourceLines = new java.util.HashSet<>();
        String warehouse = null;
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (WmsIntegrationDocumentItemDTO item : request.getItems()) {
            requireReceipt(item != null, "明细不能为空");
            requireReceipt(item.getLineNo() != null && item.getLineNo() > 0 && lines.add(item.getLineNo()), "明细行号必须为正整数且不能重复");
            Integer sourceLine = item.getSourceLineNo() == null ? item.getLineNo() : item.getSourceLineNo();
            requireReceipt(sourceLine > 0 && sourceLines.add(sourceLine), "来源行号必须为正整数且不能重复");
            requireReceipt(validText(item.getWarehouseCode(), 20), "仓库编码不能为空");
            if (warehouse == null) warehouse = item.getWarehouseCode();
            requireReceipt(Objects.equals(warehouse, item.getWarehouseCode()), "同一申请只能包含一个仓库");
            requireReceipt(validText(item.getMaterialCode(), 20) && validText(item.getSkuCode(), 64), "物料和 SKU 编码不能为空");
            requireReceipt(materialType.equals(item.getMaterialType()), "物料类型必须为 " + materialType);
            requireReceipt(validText(item.getMaterialNameSnapshot(), 255) && validText(item.getUnitCode(), 64)
                    && validText(item.getUnitNameSnapshot(), 64), "物料名称和单位快照不能为空");
            java.math.BigDecimal quantity = item.getRequiredQuantity();
            requireReceipt(quantity != null && quantity.signum() > 0, "申请数量必须大于零");
            requireReceipt(quantity.stripTrailingZeros().scale() <= 2, "当前库存数量最多支持两位小数");
            sourceAmount(item);
            total = total.add(quantity);
            requireReceipt(total.compareTo(new java.math.BigDecimal("1000000000000000000")) < 0, "申请数量合计超出库存字段范围");
        }
    }

    private static java.math.BigDecimal sourceAmount(WmsIntegrationDocumentItemDTO item) {
        var price = item.getPrice();
        var amount = item.getTotalPrice();
        for (var value : new java.math.BigDecimal[]{price, amount}) {
            requireReceipt(value == null || (value.signum() >= 0 && value.stripTrailingZeros().scale() <= 2
                    && value.compareTo(new java.math.BigDecimal("100000000000000")) < 0),
                    "来源单价及金额必须非负、最多两位小数且不超出字段范围");
        }
        requireReceipt(amount == null || price != null, "来源金额需要同时提供单价");
        if (price == null) return null;
        var calculated = price.multiply(item.getRequiredQuantity()).setScale(2, java.math.RoundingMode.HALF_UP);
        requireReceipt(calculated.compareTo(new java.math.BigDecimal("100000000000000")) < 0, "来源行金额超出字段范围");
        requireReceipt(amount == null || amount.compareTo(calculated) == 0, "来源金额必须等于申请数量乘单价（两位小数）");
        return amount == null ? calculated : amount;
    }

    private static boolean validText(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    private static void requireReceipt(boolean condition, String reason) {
        if (!condition) throw exception(cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.INTEGRATION_RECEIPT_REQUEST_INVALID, reason);
    }

    private WmsIntegrationAcceptedRespDTO createPurchaseReceiptOrder(WmsOrderRequestDTO request) {

        String warehouseCode = request.getItems().getFirst().getWarehouseCode();
        WmsWarehouseDO warehouse = warehouseMapper.selectByCode(warehouseCode);
        if (warehouse == null) {
            throw exception(WAREHOUSE_NOT_EXISTS);
        }
        String materialType = "PURCHASE_RECEIPT".equals(request.getOrderType()) ? "RAW_MATERIAL" : "FINISHED_GOODS";
        requireReceipt(materialType.equals(warehouse.getWarehouseType()), "仓库类型必须为 " + materialType);
        WmsMerchantDO merchant = request.getPartnerCode() == null || request.getPartnerCode().isBlank()
                ? null : merchantMapper.selectByCode(request.getPartnerCode());
        if (request.getPartnerCode() != null && !request.getPartnerCode().isBlank() && merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }

        List<WmsReceiptOrderDetailSaveReqVO> details = request.getItems().stream().map(item -> {
            if (!Objects.equals(warehouseCode, item.getWarehouseCode())) {
                throw exception(WAREHOUSE_NOT_EXISTS);
            }
            WmsItemSkuDO sku = itemSkuMapper.selectByCode(item.getSkuCode());
            if (sku == null) {
                throw exception(ITEM_SKU_NOT_EXISTS);
            }
            var material = itemMapper.selectById(sku.getItemId());
            requireReceipt(material != null && Objects.equals(material.getCode(), item.getMaterialCode())
                    && materialType.equals(material.getMaterialType()), "SKU 不属于申请物料或物料类型不符");
            return new WmsReceiptOrderDetailSaveReqVO().setSkuId(sku.getId())
                    .setSourceLineNo(item.getSourceLineNo() == null ? item.getLineNo() : item.getSourceLineNo())
                    .setSourceSnapshotJson(cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString(item))
                    .setQuantity(item.getRequiredQuantity())
                    .setPrice(item.getPrice()).setTotalPrice(sourceAmount(item));
        }).toList();

        String orderNo = "WMS-" + request.getSourceModule() + "-" + request.getSourceOrderNo();
        WmsReceiptOrderSaveReqVO save = new WmsReceiptOrderSaveReqVO().setNo(orderNo)
                .setType("ERP".equals(request.getSourceModule()) ? WmsReceiptOrderTypeEnum.PURCHASE.getType()
                        : WmsReceiptOrderTypeEnum.PRODUCTION.getType()).setOrderTime(request.getOccurredAt())
                .setBizOrderNo(request.getSourceOrderNo()).setMerchantId(merchant == null ? null : merchant.getId())
                .setSourceModule(request.getSourceModule()).setBusinessChainNo(request.getBusinessChainNo())
                .setWarehouseId(warehouse.getId()).setDetails(details);
        receiptOrderService.createReceiptOrder(save);
        return new WmsIntegrationAcceptedRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setTargetOrderNo(orderNo).setBusinessStatus("RECEIVED")
                .setProcessedVersion(request.getEventVersion());
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createCustomerReturnReceipt(WmsOrderRequestDTO reqDTO) {
        throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Override
    public CommonResult<WmsIntegrationAcceptedRespDTO> createPurchaseReturnShipment(WmsOrderRequestDTO reqDTO) {
        throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Override
    public CommonResult<WmsInventoryDecisionRespDTO> getInventoryDecision(Long tenantId, String saleOrderNo) {
        validateTenant(tenantId);
        throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
    }

    @Override
    public CommonResult<WmsIntegrationRequestRespDTO> getOrderExecution(Long tenantId, String sourceOrderNo,
                                                                         String orderType) {
        validateTenant(tenantId);
        if ("SALE_SHIPMENT".equals(orderType) || "PRODUCTION_SHIPMENT".equals(orderType)) {
            return shipmentExecution(sourceOrderNo, orderType);
        }
        if (!"PURCHASE_RECEIPT".equals(orderType) && !"PRODUCTION_RECEIPT".equals(orderType))
            throw exception(INTEGRATION_FEATURE_NOT_ENABLED);
        requireReceipt(sourceOrderNo != null && !sourceOrderNo.isBlank(), "来源单号不能为空");
        var query = new cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderPageReqVO();
        query.setBizOrderNo(sourceOrderNo);
        query.setPageSize(cn.iocoder.txgy.framework.common.pojo.PageParam.PAGE_SIZE_NONE);
        var order = receiptOrderService.getReceiptOrderPage(query).getList().stream()
                .filter(row -> sourceOrderNo.equals(row.getBizOrderNo())
                        && ("PURCHASE_RECEIPT".equals(orderType) ? "ERP" : "MES").equals(row.getSourceModule()))
                .findFirst().orElseThrow(() -> exception(cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.RECEIPT_ORDER_NOT_EXISTS));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("executionStatus", order.getExecutionStatus());
        data.put("approvalStatus", order.getApprovalStatus());
        data.put("executionTime", order.getExecutionTime());
        data.put("executionError", order.getExecutionError());
        data.put("notificationStatus", cn.iocoder.txgy.module.wms.service.integration.WmsReceiptExecutionService.notificationStatus(order));
        data.put("notificationError", order.getNotificationError());
        var lines = receiptDetailMapper.selectListByOrderId(order.getId());
        data.put("sourceModule", order.getSourceModule());
        data.put("sourceOrderNo", order.getBizOrderNo());
        data.put("businessChainNo", order.getBusinessChainNo());
        data.put("orderType", orderType);
        data.put("completedQuantity", Integer.valueOf(1).equals(order.getExecutionStatus())
                ? lines.stream().map(d -> d.getQualifiedQuantity() == null ? java.math.BigDecimal.ZERO : d.getQualifiedQuantity())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add) : java.math.BigDecimal.ZERO);
        data.put("items", lines.stream().map(d -> sourceItem(d.getSourceSnapshotJson())
                .setSourceLineNo(d.getSourceLineNo()).setRequiredQuantity(d.getQuantity())
                .setActualQuantity(d.getActualQuantity()).setQualifiedQuantity(d.getQualifiedQuantity())
                .setUnqualifiedQuantity(d.getUnqualifiedQuantity())).toList());
        return success(new WmsIntegrationRequestRespDTO().setAggregateType("RECEIPT").setAggregateNo(order.getNo())
                .setProcessingStatus(cn.iocoder.txgy.module.wms.service.integration.WmsReceiptExecutionService.executionStatus(order))
                .setProcessedEventVersion(Integer.valueOf(1).equals(order.getExecutionStatus()) ? 1L : 0L)
                .setResultCode(0).setResultData(data).setUpdatedAt(order.getUpdateTime()));
    }

    private CommonResult<WmsIntegrationRequestRespDTO> shipmentExecution(String sourceNo, String type) {
        requireReceipt(validText(sourceNo, 56), "来源单号不能为空且不能超过 56 字符");
        var q = new cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderPageReqVO();
        q.setBizOrderNo(sourceNo);
        q.setPageSize(cn.iocoder.txgy.framework.common.pojo.PageParam.PAGE_SIZE_NONE);
        String source = "SALE_SHIPMENT".equals(type) ? "ERP" : "MES";
        var order = shipmentOrderService.getShipmentOrderPage(q).getList().stream()
                .filter(row -> sourceNo.equals(row.getBizOrderNo()) && source.equals(row.getSourceModule()))
                .findFirst().orElseThrow(() -> exception(cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.SHIPMENT_ORDER_NOT_EXISTS));
        var view = new cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO()
                .setStatus(order.getStatus()).setApprovalStatus(order.getApprovalStatus()).setExecutionStatus(order.getExecutionStatus());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("approvalStatus", order.getApprovalStatus());
        data.put("executionStatus", order.getExecutionStatus());
        data.put("executionTime", order.getExecutionTime());
        data.put("executionError", order.getExecutionError());
        data.put("sourceModule", source);
        data.put("orderType", type);
        data.put("sourceOrderNo", sourceNo);
        data.put("completedQuantity", Integer.valueOf(1).equals(order.getExecutionStatus())
                ? order.getTotalQuantity() : java.math.BigDecimal.ZERO);
        data.put("notificationStatus", cn.iocoder.txgy.module.wms.service.integration.WmsShipmentExecutionService.notificationStatus(order));
        data.put("notificationError", order.getNotificationError());
        data.put("businessChainNo", order.getBusinessChainNo());
        data.put("items", shipmentDetailMapper.selectListByOrderId(order.getId()).stream()
                .map(d -> sourceItem(d.getSourceSnapshotJson()).setSourceLineNo(d.getSourceLineNo())
                    .setRequiredQuantity(d.getQuantity()).setActualQuantity(Integer.valueOf(1).equals(order.getExecutionStatus())
                            ? d.getQuantity() : java.math.BigDecimal.ZERO)).toList());
        return success(new WmsIntegrationRequestRespDTO().setAggregateType("SHIPMENT").setAggregateNo(order.getNo())
                .setProcessingStatus(Integer.valueOf(2).equals(order.getExecutionStatus()) ? "EXECUTION_FAILED"
                        : cn.iocoder.txgy.module.wms.service.integration.WmsReceiptExecutionService.executionStatus(view))
                .setResultCode(0).setResultData(data).setUpdatedAt(order.getUpdateTime()));
    }

    private WmsIntegrationDocumentItemDTO sourceItem(String json) {
        return json == null ? new WmsIntegrationDocumentItemDTO()
                : cn.iocoder.txgy.framework.common.util.json.JsonUtils.parseObject(json, WmsIntegrationDocumentItemDTO.class);
    }
}
