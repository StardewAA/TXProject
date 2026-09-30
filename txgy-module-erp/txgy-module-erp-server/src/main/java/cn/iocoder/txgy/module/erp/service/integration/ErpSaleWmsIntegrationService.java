package cn.iocoder.txgy.module.erp.service.integration;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpInventoryDecisionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.sale.vo.out.ErpSaleOutSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleOutDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleOrderMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductUnitService;
import cn.iocoder.txgy.module.erp.service.sale.ErpCustomerService;
import cn.iocoder.txgy.module.erp.service.sale.ErpSaleOutService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsInventoryDecisionReqDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsInventoryDecisionRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsReservationReleaseReqDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_ORDER_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_STATE_STALE;
import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.*;

@Service
@Slf4j
public class ErpSaleWmsIntegrationService {

    public static final String WAREHOUSE_DECISION_PENDING = "DECISION_PENDING";
    public static final String WAREHOUSE_RESERVED_FULL = "RESERVED_FULL";
    public static final String WAREHOUSE_RESERVED_PARTIAL = "RESERVED_PARTIAL";
    public static final String WAREHOUSE_SHORTAGE = "SHORTAGE";
    public static final String WAREHOUSE_REJECTED = "REJECTED";
    public static final String WAREHOUSE_FAILED = "FAILED";
    public static final String WAREHOUSE_CANCEL_PENDING = "CANCEL_PENDING";

    @Resource
    private ErpSaleOrderMapper saleOrderMapper;
    @Resource
    private ErpSaleOrderItemMapper saleOrderItemMapper;
    @Resource
    private ErpProductService productService;
    @Resource
    private ErpProductUnitService productUnitService;
    @Resource
    private ErpCustomerService customerService;
    @Resource
    private ErpWmsIntegrationGateway integrationGateway;
    /** 销售出库单：WMS 发货回传后据此落单，避免绕过 erp_sale_out */
    @Resource
    private ErpSaleOutService saleOutService;
    /** 用 WMS 仓库编码反查 ERP 仓库编号（出库明细必须落 ERP 仓库） */
    @Resource
    private ErpWarehouseMapper warehouseMapper;
    @Autowired
    @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;

    /** 在 ERP 多级审批最终通过的事务中准备请求，提交成功后再调用 WMS。 */
    public void prepareInventoryDecisionAfterCommit(Long saleOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        WmsInventoryDecisionReqDTO request = buildInventoryDecisionRequest(saleOrderId, tenantId);
        recordService.createOutbound(request.getRequestId(), "INVENTORY_DECISION", request.getSourceOrderNo(),
                request.getEventVersion(), request);
        saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId)
                .setBusinessChainNo(request.getBusinessChainNo())
                .setWarehouseStatus(WAREHOUSE_DECISION_PENDING)
                .setWmsEventVersion(request.getEventVersion() - 1)
                .setWmsFailureReason(null));

        Runnable sender = () -> TenantUtils.execute(tenantId, () -> sendInventoryDecision(saleOrderId, request));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.run();
            }
        });
    }

    private WmsInventoryDecisionReqDTO buildInventoryDecisionRequest(Long saleOrderId, Long tenantId) {
        ErpSaleOrderDO order = saleOrderMapper.selectById(saleOrderId);
        if (order == null) {
            throw exception(WMS_CALLBACK_ORDER_NOT_EXISTS);
        }
        ErpCustomerDO customer = customerService.validateCustomer(order.getCustomerId());
        List<ErpSaleOrderItemDO> orderItems = new ArrayList<>(saleOrderItemMapper.selectListByOrderId(saleOrderId));
        orderItems.sort(Comparator.comparing(ErpSaleOrderItemDO::getId));
        List<ErpProductDO> products = productService.validProductList(convertSet(orderItems, ErpSaleOrderItemDO::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(products, ErpProductDO::getId);
        Map<Long, ErpProductUnitDO> unitMap = productUnitService.getProductUnitMap(
                convertSet(products, ErpProductDO::getUnitId));

        String businessChainNo = order.getBusinessChainNo() != null ? order.getBusinessChainNo() : "BC-SO-" + order.getNo();
        long eventVersion = order.getWmsEventVersion() == null ? 1L : order.getWmsEventVersion() + 1;
        WmsInventoryDecisionReqDTO request = new WmsInventoryDecisionReqDTO()
                .setBusinessChainNo(businessChainNo).setSourceOrderNo(order.getNo())
                .setCustomerCode(requireValue(customer.getPartnerCode(), "客户统一编码"))
                .setRequiredAt(order.getOrderTime()).setEventVersion(eventVersion);
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        List<WmsIntegrationDocumentItemDTO> items = new ArrayList<>(orderItems.size());
        for (int index = 0; index < orderItems.size(); index++) {
            ErpSaleOrderItemDO orderItem = orderItems.get(index);
            ErpProductDO product = productMap.get(orderItem.getProductId());
            ErpProductUnitDO unit = unitMap.get(product.getUnitId());
            items.add(new WmsIntegrationDocumentItemDTO()
                    .setLineNo(index + 1).setMaterialCode(requireValue(product.getMaterialCode(), "统一物料编码"))
                    .setSkuCode(product.getMaterialCode() + "-DEFAULT")
                    .setMaterialType(requireValue(product.getMaterialType(), "物料类型"))
                    .setMaterialNameSnapshot(product.getName()).setSpecificationSnapshot(product.getStandard())
                    .setUnitCode(requireValue(unit.getCode(), "统一单位编码")).setUnitNameSnapshot(unit.getName())
                    .setWarehouseCode(orderItem.getWarehouseCode()).setRequiredQuantity(orderItem.getCount())
                    .setQualityRequiredSnapshot(Boolean.TRUE.equals(product.getQualityRequired())));
        }
        request.setItems(items);
        return request;
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + "未配置，无法提交 WMS");
        }
        return value;
    }

    private void sendInventoryDecision(Long saleOrderId, WmsInventoryDecisionReqDTO request) {
        try {
            WmsInventoryDecisionRespDTO response = integrationGateway.createInventoryDecision(request);
            applyInventoryDecision(saleOrderId, response);
            recordService.markSuccess(request.getRequestId(), response);
        } catch (Exception ex) {
            saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId)
                    .setWarehouseStatus(WAREHOUSE_FAILED).setWmsFailureReason(ex.getMessage()));
            recordService.markFailed(request.getRequestId(), ex);
            log.error("[sendInventoryDecision][saleOrderId({}) requestId({}) WMS 库存判断失败]",
                    saleOrderId, request.getRequestId(), ex);
        }
    }

    /** 订单反审批、取消或修改前释放尚未核销的 WMS 预留。 */
    public void prepareReservationReleaseAfterCommit(Long saleOrderId, String reasonCode, String reason) {
        ErpSaleOrderDO order = saleOrderMapper.selectById(saleOrderId);
        if (order == null) {
            throw exception(WMS_CALLBACK_ORDER_NOT_EXISTS);
        }
        if (order.getReservationNo() == null || order.getReservationNo().isBlank()) {
            saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId).setWarehouseStatus("NOT_SUBMITTED"));
            return;
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long eventVersion = order.getWmsEventVersion() == null ? 1L : order.getWmsEventVersion() + 1;
        WmsReservationReleaseReqDTO request = new WmsReservationReleaseReqDTO()
                .setBusinessChainNo(order.getBusinessChainNo()).setSaleOrderNo(order.getNo())
                .setReservationNo(order.getReservationNo()).setReasonCode(reasonCode).setReason(reason)
                .setEventVersion(eventVersion);
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), "RESERVATION", order.getReservationNo(),
                eventVersion, request);
        saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId)
                .setWarehouseStatus(WAREHOUSE_CANCEL_PENDING).setWmsFailureReason(null));
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> sendReservationRelease(saleOrderId, request));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.run();
            }
        });
    }

    private void sendReservationRelease(Long saleOrderId, WmsReservationReleaseReqDTO request) {
        try {
            WmsIntegrationAcceptedRespDTO response = integrationGateway.releaseReservation(request);
            if (response == null || !Boolean.TRUE.equals(response.getAccepted())
                    || !RESERVATION_RELEASED.equals(response.getBusinessStatus())) {
                throw new IllegalStateException("WMS 未完成库存预留释放");
            }
            saleOrderMapper.clearReservationAfterRelease(saleOrderId, response.getProcessedVersion());
            recordService.markSuccess(request.getRequestId(), response);
        } catch (Exception ex) {
            saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId)
                    .setWarehouseStatus(WAREHOUSE_FAILED).setWmsFailureReason(ex.getMessage()));
            recordService.markFailed(request.getRequestId(), ex);
            log.error("[sendReservationRelease][saleOrderId({}) requestId({}) 释放 WMS 预留失败]",
                    saleOrderId, request.getRequestId(), ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void receiveInventoryDecision(ErpInventoryDecisionCallbackReqDTO request) {
        if (recordService.hasProcessedInbound(request.getRequestId())) {
            return;
        }
        validateOccurredAt(request.getOccurredAt());
        ErpSaleOrderDO order = saleOrderMapper.selectByNo(request.getSaleOrderNo());
        if (order == null) {
            throw exception(WMS_CALLBACK_ORDER_NOT_EXISTS);
        }
        long currentVersion = order.getWmsEventVersion() == null ? 0 : order.getWmsEventVersion();
        if (request.getEventVersion() <= currentVersion || request.getEventVersion() > currentVersion + 1) {
            throw exception(WMS_CALLBACK_STATE_STALE);
        }
        WmsInventoryDecisionRespDTO response = new WmsInventoryDecisionRespDTO()
                .setAccepted(true).setRequestId(request.getRequestId()).setDecisionNo(request.getDecisionNo())
                .setStockDecision(request.getStockDecision()).setReservationNo(request.getReservationNo())
                .setProcessedEventVersion(request.getEventVersion())
                .setItems(request.getItems().stream().map(this::convertItem).toList());
        applyInventoryDecision(order.getId(), response);
        recordService.createInboundSuccess(request.getRequestId(), "INVENTORY_DECISION",
                request.getSaleOrderNo(), request.getEventVersion(), request);
    }

    @Transactional(rollbackFor = Exception.class)
    public void receiveExecutionResult(ErpWmsExecutionCallbackReqDTO request) {
        if (recordService.hasProcessedInbound(request.getRequestId())) {
            return;
        }
        validateOccurredAt(request.getOccurredAt());
        if (!"SALE_SHIPMENT".equals(request.getOrderType())) {
            throw new IllegalArgumentException("当前回调仅支持 SALE_SHIPMENT，实际：" + request.getOrderType());
        }
        ErpSaleOrderDO order = saleOrderMapper.selectByNo(request.getSourceOrderNo());
        if (order == null) {
            throw exception(WMS_CALLBACK_ORDER_NOT_EXISTS);
        }
        long currentVersion = order.getWmsEventVersion() == null ? 0 : order.getWmsEventVersion();
        if (request.getEventVersion() <= currentVersion || request.getEventVersion() > currentVersion + 1) {
            throw exception(WMS_CALLBACK_STATE_STALE);
        }
        List<ErpSaleOrderItemDO> orderItems = new ArrayList<>(saleOrderItemMapper.selectListByOrderId(order.getId()));
        orderItems.sort(Comparator.comparing(ErpSaleOrderItemDO::getId));
        Map<Integer, ErpWmsDocumentItemDTO> resultMap = new HashMap<>();
        if (request.getItems() != null) {
            request.getItems().forEach(item -> resultMap.put(item.getLineNo(), item));
        }
        java.math.BigDecimal totalOut = java.math.BigDecimal.ZERO;
        for (int index = 0; index < orderItems.size(); index++) {
            ErpWmsDocumentItemDTO result = resultMap.get(index + 1);
            if (result == null || result.getActualQuantity() == null) {
                continue;
            }
            saleOrderItemMapper.updateById(new ErpSaleOrderItemDO().setId(orderItems.get(index).getId())
                    .setOutCount(result.getActualQuantity()).setWarehouseCode(result.getWarehouseCode()));
            totalOut = totalOut.add(result.getActualQuantity());
        }
        String warehouseStatus = mapExecutionStatus(request.getExecutionStatus());
        ErpSaleOrderDO update = new ErpSaleOrderDO().setId(order.getId())
                .setWmsShipmentOrderNo(request.getWmsOrderNo()).setWarehouseStatus(warehouseStatus)
                .setWmsEventVersion(request.getEventVersion()).setWmsFailureReason(request.getReason());
        if ("COMPLETED".equals(request.getExecutionStatus())) {
            update.setOutCount(totalOut);
        }
        saleOrderMapper.updateById(update);
        // 落销售出库单：原实现只把实发数量写进订单 out_count，
        // 既不落 erp_sale_out 也不扣 ERP 业务账库存，导致销售数据无法统计。
        if ("COMPLETED".equals(request.getExecutionStatus())) {
            createSaleOutFromExecution(order, orderItems, resultMap, request);
        }
        recordService.createInboundSuccess(request.getRequestId(), "SALE_SHIPMENT",
                request.getSourceOrderNo(), request.getEventVersion(), request);
    }

    /**
     * 由 WMS 发货回传生成并审批销售出库单。
     *
     * <p>为什么必须落单：销售数量/金额的统计口径是<b>销售出库单据</b>，而非库存差。
     * 原实现只把实发数量写进销售订单的 out_count，导致 {@code erp_sale_out} 长期为空、
     * 销售数据无法统计（第 4 批绩效报表的取数前提）。</p>
     *
     * <p>复用既有 Service（createSaleOut + updateSaleOutStatus），因此库存扣减、
     * 订单出库数量回算等逻辑完全一致，不产生第二套口径。</p>
     */
    private void createSaleOutFromExecution(ErpSaleOrderDO order, List<ErpSaleOrderItemDO> orderItems,
                                            Map<Integer, ErpWmsDocumentItemDTO> resultMap,
                                            ErpWmsExecutionCallbackReqDTO request) {
        // 幂等保护：该订单已有出库单则不再自动生成，避免重复计数
        List<ErpSaleOutDO> existing = saleOutService.getSaleOutListByOrderId(order.getId());
        if (CollUtil.isNotEmpty(existing)) {
            log.warn("[createSaleOutFromExecution][订单 {} 已存在 {} 张出库单，跳过自动生成]",
                    order.getNo(), existing.size());
            return;
        }
        List<ErpSaleOutSaveReqVO.Item> items = new ArrayList<>();
        for (int index = 0; index < orderItems.size(); index++) {
            ErpSaleOrderItemDO orderItem = orderItems.get(index);
            ErpWmsDocumentItemDTO result = resultMap.get(index + 1);
            if (result == null || result.getActualQuantity() == null
                    || result.getActualQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue; // 未实发的行不落出库明细
            }
            Long warehouseId = resolveWarehouseId(result.getWarehouseCode(), orderItem.getWarehouseCode());
            if (warehouseId == null) {
                log.warn("[createSaleOutFromExecution][订单 {} 第 {} 行仓库编码 {} 无法映射到 ERP 仓库，跳过该行]",
                        order.getNo(), index + 1, result.getWarehouseCode());
                continue;
            }
            items.add(new ErpSaleOutSaveReqVO.Item()
                    .setOrderItemId(orderItem.getId())
                    .setProductId(orderItem.getProductId())
                    .setProductUnitId(orderItem.getProductUnitId())
                    .setWarehouseId(warehouseId)
                    .setCount(result.getActualQuantity())
                    .setProductPrice(orderItem.getProductPrice())
                    .setTaxPercent(orderItem.getTaxPercent()));
        }
        if (items.isEmpty()) {
            log.warn("[createSaleOutFromExecution][订单 {} 无可用实发明细，跳过自动生成]", order.getNo());
            return;
        }
        Long saleOutId = saleOutService.createSaleOut(new ErpSaleOutSaveReqVO()
                .setOrderId(order.getId())
                .setOutTime(LocalDateTime.now())
                .setSaleUserId(order.getSaleUserId())
                .setAccountId(order.getAccountId())
                // discountPercent 是优惠率：0 = 不打折
                .setDiscountPercent(BigDecimal.ZERO)
                // otherPrice 必须显式传 0：calculateTotalPrice 对其直接做加法，为 null 会 NPE
                .setOtherPrice(BigDecimal.ZERO)
                .setRemark("WMS 发货回传自动生成：" + request.getWmsOrderNo())
                .setItems(items));
        saleOutService.updateSaleOutStatus(saleOutId, ErpAuditStatus.APPROVE.getStatus());
        log.info("[createSaleOutFromExecution][订单 {} 自动生成并审批出库单 {}（{} 行）]",
                order.getNo(), saleOutId, items.size());
    }

    /** 用 WMS 仓库编码反查 ERP 仓库编号；WMS 未给出编码时退回订单行上的编码 */
    private Long resolveWarehouseId(String wmsCode, String fallbackCode) {
        String code = wmsCode != null && !wmsCode.isBlank() ? wmsCode : fallbackCode;
        if (code == null || code.isBlank()) {
            return null;
        }
        ErpWarehouseDO warehouse = warehouseMapper.selectByWmsWarehouseCode(code.trim());
        return warehouse != null ? warehouse.getId() : null;
    }

    private String mapExecutionStatus(String status) {
        if ("COMPLETED".equals(status)) return "SHIPPED";
        if ("RECEIVED".equals(status) || "APPROVING".equals(status) || "APPROVED".equals(status)) {
            return "SHIPMENT_APPROVING";
        }
        if ("REJECTED".equals(status) || "CANCELLED".equals(status)) return WAREHOUSE_REJECTED;
        return WAREHOUSE_FAILED;
    }

    private void validateOccurredAt(LocalDateTime occurredAt) {
        LocalDateTime now = LocalDateTime.now();
        if (occurredAt.isBefore(now.minusMinutes(10)) || occurredAt.isAfter(now.plusMinutes(10))) {
            throw new IllegalArgumentException("WMS 回调时间超出允许窗口");
        }
    }

    private WmsIntegrationDocumentItemDTO convertItem(ErpWmsDocumentItemDTO source) {
        return new WmsIntegrationDocumentItemDTO().setLineNo(source.getLineNo())
                .setMaterialCode(source.getMaterialCode()).setSkuCode(source.getSkuCode())
                .setWarehouseCode(source.getWarehouseCode()).setRequiredQuantity(source.getRequiredQuantity())
                .setReservedQuantity(source.getReservedQuantity()).setShortageQuantity(source.getShortageQuantity());
    }

    private void applyInventoryDecision(Long saleOrderId, WmsInventoryDecisionRespDTO response) {
        if (response == null || !Boolean.TRUE.equals(response.getAccepted())) {
            throw new IllegalStateException("WMS 未接受库存判断请求");
        }
        ErpSaleOrderDO order = saleOrderMapper.selectById(saleOrderId);
        long responseVersion = response.getProcessedEventVersion() == null ? 0 : response.getProcessedEventVersion();
        long currentVersion = order.getWmsEventVersion() == null ? 0 : order.getWmsEventVersion();
        if (responseVersion < currentVersion) {
            return;
        }
        List<ErpSaleOrderItemDO> orderItems = new ArrayList<>(saleOrderItemMapper.selectListByOrderId(saleOrderId));
        orderItems.sort(Comparator.comparing(ErpSaleOrderItemDO::getId));
        Map<Integer, WmsIntegrationDocumentItemDTO> responseItemMap = new HashMap<>();
        if (response.getItems() != null) {
            response.getItems().forEach(item -> responseItemMap.put(item.getLineNo(), item));
        }
        for (int index = 0; index < orderItems.size(); index++) {
            WmsIntegrationDocumentItemDTO resultItem = responseItemMap.get(index + 1);
            if (resultItem == null) {
                continue;
            }
            saleOrderItemMapper.updateById(new ErpSaleOrderItemDO().setId(orderItems.get(index).getId())
                    .setWarehouseCode(resultItem.getWarehouseCode())
                    .setReservedCount(resultItem.getReservedQuantity())
                    .setShortageCount(resultItem.getShortageQuantity()));
        }
        saleOrderMapper.updateById(new ErpSaleOrderDO().setId(saleOrderId)
                .setWarehouseStatus(mapWarehouseStatus(response.getStockDecision()))
                .setInventoryDecisionNo(response.getDecisionNo()).setReservationNo(response.getReservationNo())
                .setWmsEventVersion(responseVersion).setWmsFailureReason(null));
    }

    private String mapWarehouseStatus(String decision) {
        if (DECISION_FULL.equals(decision)) return WAREHOUSE_RESERVED_FULL;
        if (DECISION_PARTIAL.equals(decision)) return WAREHOUSE_RESERVED_PARTIAL;
        if (DECISION_SHORTAGE.equals(decision)) return WAREHOUSE_SHORTAGE;
        if (DECISION_REJECTED.equals(decision)) return WAREHOUSE_REJECTED;
        return WAREHOUSE_DECISION_PENDING;
    }

}
