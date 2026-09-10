package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductUnitDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.*;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpCustomerDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleReturnDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.sale.ErpSaleReturnItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.stock.ErpWarehouseDO;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductUnitMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.*;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpCustomerMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleReturnItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.sale.ErpSaleReturnMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.stock.ErpWarehouseMapper;
import cn.iocoder.txgy.module.infra.api.file.FileApi;
import cn.iocoder.txgy.module.infra.api.file.dto.FileMetadataRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationAttachmentDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
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

import static cn.iocoder.txgy.module.wms.enums.integration.WmsIntegrationConstants.SOURCE_ERP;

/** ERP 采购入库、客户退货和采购退货的 WMS 提交与结果回调。 */
@Service
@Slf4j
public class ErpWmsBusinessDocumentService {

    public static final String PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    public static final String CUSTOMER_RETURN_RECEIPT = "CUSTOMER_RETURN_RECEIPT";
    public static final String PURCHASE_RETURN_SHIPMENT = "PURCHASE_RETURN_SHIPMENT";

    @Resource private ErpPurchaseInMapper purchaseInMapper;
    @Resource private ErpPurchaseInItemMapper purchaseInItemMapper;
    @Resource private ErpPurchaseReturnMapper purchaseReturnMapper;
    @Resource private ErpPurchaseReturnItemMapper purchaseReturnItemMapper;
    @Resource private ErpSaleReturnMapper saleReturnMapper;
    @Resource private ErpSaleReturnItemMapper saleReturnItemMapper;
    @Resource private ErpProductMapper productMapper;
    @Resource private ErpProductUnitMapper unitMapper;
    @Resource private ErpWarehouseMapper warehouseMapper;
    @Resource private ErpSupplierMapper supplierMapper;
    @Resource private ErpCustomerMapper customerMapper;
    @Resource private FileApi fileApi;
    @Resource private ErpWmsIntegrationGateway integrationGateway;
    @Autowired @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;

    public void preparePurchaseReceiptAfterCommit(Long id) {
        ErpPurchaseInDO document = requireDocument(purchaseInMapper.selectById(id), "采购入库单");
        ErpSupplierDO supplier = requireDocument(supplierMapper.selectById(document.getSupplierId()), "供应商");
        List<SourceItem> items = purchaseInItemMapper.selectListByInId(id).stream()
                .map(item -> new SourceItem(item.getId(), item.getProductId(), item.getWarehouseId(), item.getCount()))
                .toList();
        prepareAfterCommit(id, PURCHASE_RECEIPT, document.getNo(), document.getOrderNo(),
                document.getBusinessChainNo(), document.getWmsEventVersion(), supplier.getPartnerCode(),
                document.getFileUrl(), items, true);
    }

    public void prepareCustomerReturnReceiptAfterCommit(Long id) {
        ErpSaleReturnDO document = requireDocument(saleReturnMapper.selectById(id), "客户退货单");
        ErpCustomerDO customer = requireDocument(customerMapper.selectById(document.getCustomerId()), "客户");
        List<SourceItem> items = saleReturnItemMapper.selectListByReturnId(id).stream()
                .map(item -> new SourceItem(item.getId(), item.getProductId(), item.getWarehouseId(), item.getCount()))
                .toList();
        prepareAfterCommit(id, CUSTOMER_RETURN_RECEIPT, document.getNo(), document.getOrderNo(),
                document.getBusinessChainNo(), document.getWmsEventVersion(), customer.getPartnerCode(),
                document.getFileUrl(), items, false);
    }

    public void preparePurchaseReturnShipmentAfterCommit(Long id) {
        ErpPurchaseReturnDO document = requireDocument(purchaseReturnMapper.selectById(id), "采购退货单");
        ErpSupplierDO supplier = requireDocument(supplierMapper.selectById(document.getSupplierId()), "供应商");
        List<SourceItem> items = purchaseReturnItemMapper.selectListByReturnId(id).stream()
                .map(item -> new SourceItem(item.getId(), item.getProductId(), item.getWarehouseId(), item.getCount()))
                .toList();
        prepareAfterCommit(id, PURCHASE_RETURN_SHIPMENT, document.getNo(), document.getOrderNo(),
                document.getBusinessChainNo(), document.getWmsEventVersion(), supplier.getPartnerCode(),
                document.getFileUrl(), items, false);
    }

    private void prepareAfterCommit(Long id, String orderType, String sourceOrderNo, String originalOrderNo,
                                    String existingChainNo, Long currentVersion, String partnerCode,
                                    String fileUrls, List<SourceItem> sourceItems, boolean requireQualityImages) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        long eventVersion = currentVersion == null ? 1L : currentVersion + 1;
        String businessChainNo = existingChainNo != null ? existingChainNo : "BC-" + sourceOrderNo;
        WmsOrderRequestDTO request = new WmsOrderRequestDTO().setBusinessChainNo(businessChainNo)
                .setSourceOrderNo(sourceOrderNo).setOriginalOrderNo(originalOrderNo)
                .setPartnerCode(requireValue(partnerCode, "往来企业统一编码"))
                .setOrderType(orderType).setEventVersion(eventVersion)
                .setItems(buildItems(sourceItems, fileUrls, requireQualityImages));
        request.setRequestId(UUID.randomUUID().toString());
        request.setTenantId(tenantId);
        request.setSourceModule(SOURCE_ERP);
        request.setOccurredAt(LocalDateTime.now());
        recordService.createOutbound(request.getRequestId(), orderType, sourceOrderNo, eventVersion, request);
        updateDocument(id, orderType, businessChainNo, "SUBMIT_PENDING", null, currentVersion, null);

        Runnable sender = () -> TenantUtils.execute(tenantId, () -> send(id, orderType, request));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
        } else {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { sender.run(); }
            });
        }
    }

    private List<WmsIntegrationDocumentItemDTO> buildItems(List<SourceItem> sourceItems, String fileUrls,
                                                            boolean requireQualityImages) {
        List<WmsIntegrationAttachmentDTO> attachments = resolveAttachments(fileUrls);
        List<WmsIntegrationDocumentItemDTO> result = new ArrayList<>(sourceItems.size());
        for (int index = 0; index < sourceItems.size(); index++) {
            SourceItem source = sourceItems.get(index);
            ErpProductDO product = requireDocument(productMapper.selectById(source.productId()), "商品");
            ErpProductUnitDO unit = requireDocument(unitMapper.selectById(product.getUnitId()), "商品单位");
            ErpWarehouseDO warehouse = requireDocument(warehouseMapper.selectById(source.warehouseId()), "ERP 仓库");
            if (requireQualityImages && Boolean.TRUE.equals(product.getQualityRequired()) && attachments.isEmpty()) {
                throw new IllegalStateException("必检物料 " + product.getMaterialCode() + " 缺少质检图片");
            }
            result.add(new WmsIntegrationDocumentItemDTO().setLineNo(index + 1)
                    .setSourceLineNo(index + 1).setMaterialCode(requireValue(product.getMaterialCode(), "统一物料编码"))
                    .setSkuCode(product.getMaterialCode() + "-DEFAULT")
                    .setMaterialType(requireValue(product.getMaterialType(), "物料类型"))
                    .setMaterialNameSnapshot(product.getName()).setSpecificationSnapshot(product.getStandard())
                    .setUnitCode(requireValue(unit.getCode(), "统一单位编码")).setUnitNameSnapshot(unit.getName())
                    .setWarehouseCode(requireValue(warehouse.getWmsWarehouseCode(), "WMS 仓库编码"))
                    .setRequiredQuantity(source.quantity()).setQualityRequiredSnapshot(product.getQualityRequired())
                    .setAttachments(attachments));
        }
        return result;
    }

    private List<WmsIntegrationAttachmentDTO> resolveAttachments(String fileUrls) {
        if (fileUrls == null || fileUrls.isBlank()) return List.of();
        List<WmsIntegrationAttachmentDTO> result = new ArrayList<>();
        for (String rawUrl : fileUrls.split(",")) {
            String url = rawUrl.trim();
            if (url.isEmpty()) continue;
            FileMetadataRespDTO file = fileApi.getMetadataByUrl(url).getCheckedData();
            if (file == null) throw new IllegalStateException("质检附件在文件服务中不存在：" + url);
            result.add(new WmsIntegrationAttachmentDTO().setFileId(file.getId()).setUrl(file.getUrl())
                    .setFileName(file.getName()).setMimeType(
                            file.getType() == null ? "application/octet-stream" : file.getType()));
        }
        return result;
    }

    private void send(Long id, String orderType, WmsOrderRequestDTO request) {
        try {
            WmsIntegrationAcceptedRespDTO response;
            if (PURCHASE_RECEIPT.equals(orderType)) response = integrationGateway.createPurchaseReceipt(request);
            else if (CUSTOMER_RETURN_RECEIPT.equals(orderType)) response = integrationGateway.createCustomerReturnReceipt(request);
            else response = integrationGateway.createPurchaseReturnShipment(request);
            if (response == null || !Boolean.TRUE.equals(response.getAccepted())) {
                throw new IllegalStateException("WMS 未接受 " + orderType + " 请求");
            }
            updateDocument(id, orderType, request.getBusinessChainNo(),
                    response.getBusinessStatus() == null ? "RECEIVED" : response.getBusinessStatus(),
                    response.getTargetOrderNo(), response.getProcessedVersion(), null);
            recordService.markSuccess(request.getRequestId(), response);
        } catch (Exception ex) {
            updateDocument(id, orderType, request.getBusinessChainNo(), "FAILED", null,
                    request.getEventVersion() - 1, ex.getMessage());
            recordService.markFailed(request.getRequestId(), ex);
            log.error("[send][orderType({}) sourceOrderNo({}) requestId({}) WMS 提交失败]",
                    orderType, request.getSourceOrderNo(), request.getRequestId(), ex);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void receiveExecutionResult(ErpWmsExecutionCallbackReqDTO request) {
        if (recordService.hasProcessedInbound(request.getRequestId())) return;
        validateOccurredAt(request.getOccurredAt());
        if (PURCHASE_RECEIPT.equals(request.getOrderType())) {
            ErpPurchaseInDO document = requireDocument(purchaseInMapper.selectByNo(request.getSourceOrderNo()), "采购入库单");
            validateVersion(document.getWmsEventVersion(), request.getEventVersion());
            applyPurchaseReceiptItems(document.getId(), request.getItems());
            purchaseInMapper.updateById(new ErpPurchaseInDO().setId(document.getId())
                    .setWmsStatus(mapStatus(request.getExecutionStatus())).setWmsOrderNo(request.getWmsOrderNo())
                    .setWmsEventVersion(request.getEventVersion()).setWmsFailureReason(request.getReason()));
        } else if (CUSTOMER_RETURN_RECEIPT.equals(request.getOrderType())) {
            ErpSaleReturnDO document = requireDocument(saleReturnMapper.selectByNo(request.getSourceOrderNo()), "客户退货单");
            validateVersion(document.getWmsEventVersion(), request.getEventVersion());
            applySaleReturnItems(document.getId(), request.getItems());
            saleReturnMapper.updateById(new ErpSaleReturnDO().setId(document.getId())
                    .setWmsStatus(mapStatus(request.getExecutionStatus())).setWmsOrderNo(request.getWmsOrderNo())
                    .setWmsEventVersion(request.getEventVersion()).setWmsFailureReason(request.getReason()));
        } else if (PURCHASE_RETURN_SHIPMENT.equals(request.getOrderType())) {
            ErpPurchaseReturnDO document = requireDocument(purchaseReturnMapper.selectByNo(request.getSourceOrderNo()), "采购退货单");
            validateVersion(document.getWmsEventVersion(), request.getEventVersion());
            applyPurchaseReturnItems(document.getId(), request.getItems());
            purchaseReturnMapper.updateById(new ErpPurchaseReturnDO().setId(document.getId())
                    .setWmsStatus(mapStatus(request.getExecutionStatus())).setWmsOrderNo(request.getWmsOrderNo())
                    .setWmsEventVersion(request.getEventVersion()).setWmsFailureReason(request.getReason()));
        } else {
            throw new IllegalArgumentException("不支持的 WMS 业务回调类型：" + request.getOrderType());
        }
        recordService.createInboundSuccess(request.getRequestId(), request.getOrderType(),
                request.getSourceOrderNo(), request.getEventVersion(), request);
    }

    private void applyPurchaseReceiptItems(Long documentId, List<ErpWmsDocumentItemDTO> callbackItems) {
        Map<Integer, ErpWmsDocumentItemDTO> callbackMap = callbackItemMap(callbackItems);
        List<ErpPurchaseInItemDO> items = new ArrayList<>(purchaseInItemMapper.selectListByInId(documentId));
        items.sort(Comparator.comparing(ErpPurchaseInItemDO::getId));
        for (int index = 0; index < items.size(); index++) {
            ErpWmsDocumentItemDTO callback = callbackMap.get(index + 1);
            if (callback == null) continue;
            BigDecimal qualified = zeroIfNull(callback.getQualifiedQuantity());
            BigDecimal unqualified = zeroIfNull(callback.getUnqualifiedQuantity());
            BigDecimal actual = callback.getActualQuantity() == null
                    ? qualified.add(unqualified) : callback.getActualQuantity();
            if (callback.getActualQuantity() != null && actual.compareTo(qualified.add(unqualified)) != 0) {
                throw new IllegalArgumentException("采购入库实收数量不等于合格与不合格数量之和");
            }
            validateActualNotExceed(actual, items.get(index).getCount());
            purchaseInItemMapper.updateById(new ErpPurchaseInItemDO().setId(items.get(index).getId())
                    .setWmsActualCount(actual).setQualifiedCount(qualified).setUnqualifiedCount(unqualified));
        }
    }

    private void applySaleReturnItems(Long documentId, List<ErpWmsDocumentItemDTO> callbackItems) {
        Map<Integer, ErpWmsDocumentItemDTO> callbackMap = callbackItemMap(callbackItems);
        List<ErpSaleReturnItemDO> items = new ArrayList<>(saleReturnItemMapper.selectListByReturnId(documentId));
        items.sort(Comparator.comparing(ErpSaleReturnItemDO::getId));
        for (int index = 0; index < items.size(); index++) {
            ErpWmsDocumentItemDTO callback = callbackMap.get(index + 1);
            if (callback != null && callback.getActualQuantity() != null) {
                validateActualNotExceed(callback.getActualQuantity(), items.get(index).getCount());
                saleReturnItemMapper.updateById(new ErpSaleReturnItemDO().setId(items.get(index).getId())
                        .setWmsActualCount(callback.getActualQuantity()));
            }
        }
    }

    private void applyPurchaseReturnItems(Long documentId, List<ErpWmsDocumentItemDTO> callbackItems) {
        Map<Integer, ErpWmsDocumentItemDTO> callbackMap = callbackItemMap(callbackItems);
        List<ErpPurchaseReturnItemDO> items = new ArrayList<>(purchaseReturnItemMapper.selectListByReturnId(documentId));
        items.sort(Comparator.comparing(ErpPurchaseReturnItemDO::getId));
        for (int index = 0; index < items.size(); index++) {
            ErpWmsDocumentItemDTO callback = callbackMap.get(index + 1);
            if (callback != null && callback.getActualQuantity() != null) {
                validateActualNotExceed(callback.getActualQuantity(), items.get(index).getCount());
                purchaseReturnItemMapper.updateById(new ErpPurchaseReturnItemDO().setId(items.get(index).getId())
                        .setWmsActualCount(callback.getActualQuantity()));
            }
        }
    }

    private Map<Integer, ErpWmsDocumentItemDTO> callbackItemMap(List<ErpWmsDocumentItemDTO> callbackItems) {
        Map<Integer, ErpWmsDocumentItemDTO> result = new HashMap<>();
        if (callbackItems != null) {
            for (ErpWmsDocumentItemDTO item : callbackItems) {
                if (result.put(item.getLineNo(), item) != null) {
                    throw new IllegalArgumentException("WMS 回调存在重复行号：" + item.getLineNo());
                }
            }
        }
        return result;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateActualNotExceed(BigDecimal actual, BigDecimal requested) {
        if (actual.signum() < 0 || actual.compareTo(requested) > 0) {
            throw new IllegalArgumentException("WMS 实际执行数量超出 ERP 单据申请数量");
        }
    }

    public boolean supports(String orderType) {
        return PURCHASE_RECEIPT.equals(orderType) || CUSTOMER_RETURN_RECEIPT.equals(orderType)
                || PURCHASE_RETURN_SHIPMENT.equals(orderType);
    }

    private void updateDocument(Long id, String orderType, String chainNo, String status,
                                String wmsOrderNo, Long eventVersion, String failureReason) {
        if (PURCHASE_RECEIPT.equals(orderType)) {
            purchaseInMapper.updateById(new ErpPurchaseInDO().setId(id).setBusinessChainNo(chainNo)
                    .setWmsStatus(status).setWmsOrderNo(wmsOrderNo).setWmsEventVersion(eventVersion)
                    .setWmsFailureReason(failureReason));
        } else if (CUSTOMER_RETURN_RECEIPT.equals(orderType)) {
            saleReturnMapper.updateById(new ErpSaleReturnDO().setId(id).setBusinessChainNo(chainNo)
                    .setWmsStatus(status).setWmsOrderNo(wmsOrderNo).setWmsEventVersion(eventVersion)
                    .setWmsFailureReason(failureReason));
        } else {
            purchaseReturnMapper.updateById(new ErpPurchaseReturnDO().setId(id).setBusinessChainNo(chainNo)
                    .setWmsStatus(status).setWmsOrderNo(wmsOrderNo).setWmsEventVersion(eventVersion)
                    .setWmsFailureReason(failureReason));
        }
    }

    private void validateVersion(Long currentVersion, Long eventVersion) {
        long current = currentVersion == null ? 0 : currentVersion;
        if (eventVersion == null || eventVersion <= current || eventVersion > current + 1) {
            throw new IllegalStateException("WMS 回调版本已过期或存在断档");
        }
    }

    private void validateOccurredAt(LocalDateTime occurredAt) {
        LocalDateTime now = LocalDateTime.now();
        if (occurredAt == null || occurredAt.isBefore(now.minusMinutes(10)) || occurredAt.isAfter(now.plusMinutes(10))) {
            throw new IllegalArgumentException("WMS 回调时间超出允许窗口");
        }
    }

    private String mapStatus(String status) {
        if ("COMPLETED".equals(status)) return "COMPLETED";
        if ("REJECTED".equals(status) || "CANCELLED".equals(status) || "FAILED".equals(status)) return status;
        return "PROCESSING";
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalStateException(fieldName + "未配置，无法提交 WMS");
        return value;
    }

    private <T> T requireDocument(T value, String name) {
        if (value == null) throw new IllegalStateException(name + "不存在");
        return value;
    }

    private record SourceItem(Long sourceItemId, Long productId, Long warehouseId, BigDecimal quantity) {}
}
