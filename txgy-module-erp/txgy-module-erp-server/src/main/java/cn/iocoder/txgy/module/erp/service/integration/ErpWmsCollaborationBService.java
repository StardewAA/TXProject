package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveRespDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_ALREADY_CONFIRMED;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_CONFIRM_ITEM_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_STATE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_STATE_STALE;

@Service
public class ErpWmsCollaborationBService {

    @Resource
    private ErpWmsCollaborationBMapper collaborationMapper;
    @Resource
    private ErpWmsCollaborationBItemMapper collaborationItemMapper;
    @Resource
    private ErpProductMapper productMapper;
    @Resource
    private ErpWmsIntegrationRecordService recordService;
    @Resource
    private ErpPurchaseOrderService purchaseOrderService;

    @Transactional(rollbackFor = Exception.class)
    public ErpCollaborationBReceiveRespDTO receive(ErpCollaborationBReceiveReqDTO request) {
        ErpWmsCollaborationBDO existing = collaborationMapper.selectByCollaborationNo(request.getCollaborationNo());
        if (recordService.hasProcessedInbound(request.getRequestId()) && existing != null) {
            return buildResponse(request, existing);
        }
        LocalDateTime now = LocalDateTime.now();
        if (request.getOccurredAt().isBefore(now.minusMinutes(10))
                || request.getOccurredAt().isAfter(now.plusMinutes(10))) {
            throw new IllegalArgumentException("WMS 协作单请求时间超出允许窗口");
        }
        if (existing != null) {
            throw exception(WMS_CALLBACK_STATE_STALE);
        }
        for (ErpWmsDocumentItemDTO item : request.getItems()) {
            if (productMapper.selectByMaterialCode(item.getMaterialCode()) == null) {
                throw new IllegalStateException("统一物料编码不存在：" + item.getMaterialCode());
            }
        }
        ErpWmsCollaborationBDO collaboration = ErpWmsCollaborationBDO.builder()
                .collaborationNo(request.getCollaborationNo()).businessChainNo(request.getBusinessChainNo())
                .sourceProductionOrderNo(request.getSourceProductionOrderNo())
                .sourceCollaborationGNo(request.getSourceCollaborationGNo())
                .sourceSaleOrderNo(request.getSourceSaleOrderNo()).technicalStatus("RECEIVED")
                .businessStatus("PENDING_CONFIRMATION").eventVersion(request.getEventVersion())
                .requiredAt(request.getRequiredAt()).build();
        collaborationMapper.insert(collaboration);
        for (ErpWmsDocumentItemDTO item : request.getItems()) {
            collaborationItemMapper.insert(ErpWmsCollaborationBItemDO.builder()
                    .collaborationId(collaboration.getId()).lineNo(item.getLineNo())
                    .materialCode(item.getMaterialCode()).skuCode(item.getSkuCode())
                    .materialNameSnapshot(item.getMaterialNameSnapshot())
                    .specificationSnapshot(item.getSpecificationSnapshot())
                    .unitCode(item.getUnitCode()).unitNameSnapshot(item.getUnitNameSnapshot())
                    .shortageQuantity(item.getShortageQuantity()).build());
        }
        recordService.createInboundSuccess(request.getRequestId(), "COLLABORATION_B",
                request.getCollaborationNo(), request.getEventVersion(), request);
        return buildResponse(request, collaboration);
    }

    /**
     * 业务确认与技术接收严格分离。接受时补齐供应商、价格等 ERP 商务信息，
     * 并生成采购订单草稿；拒绝时仅关闭协作单，不产生采购业务单据。
     */
    @Transactional(rollbackFor = Exception.class)
    public ErpWmsCollaborationBConfirmRespVO confirm(ErpWmsCollaborationBConfirmReqVO request) {
        ErpWmsCollaborationBDO collaboration = collaborationMapper.selectById(request.getId());
        if (collaboration == null) {
            throw exception(COLLABORATION_B_NOT_EXISTS);
        }
        if ("ACCEPTED".equals(collaboration.getBusinessStatus())
                || "REJECTED".equals(collaboration.getBusinessStatus())) {
            throw exception(COLLABORATION_B_ALREADY_CONFIRMED);
        }
        if (!"PENDING_CONFIRMATION".equals(collaboration.getBusinessStatus())) {
            throw exception(COLLABORATION_B_STATE_INVALID);
        }

        String action = request.getAction().trim().toUpperCase(Locale.ROOT);
        if ("REJECTED".equals(action)) {
            int updated = collaborationMapper.updateByIdAndBusinessStatus(collaboration.getId(),
                    "PENDING_CONFIRMATION", new ErpWmsCollaborationBDO()
                            .setBusinessStatus("REJECTED").setReason(request.getReason()));
            if (updated == 0) {
                throw exception(COLLABORATION_B_STATE_INVALID);
            }
            collaboration.setBusinessStatus("REJECTED").setReason(request.getReason());
            return buildConfirmResponse(collaboration);
        }
        if (!"ACCEPTED".equals(action) || request.getSupplierId() == null
                || request.getOrderTime() == null || request.getItems() == null) {
            throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
        }

        List<ErpWmsCollaborationBItemDO> receivedItems =
                collaborationItemMapper.selectListByCollaborationId(collaboration.getId());
        Map<Integer, ErpWmsCollaborationBConfirmReqVO.Item> confirmItemMap;
        try {
            confirmItemMap = request.getItems().stream().collect(Collectors.toMap(
                    ErpWmsCollaborationBConfirmReqVO.Item::getLineNo, Function.identity()));
        } catch (IllegalStateException duplicateLine) {
            throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
        }
        if (receivedItems.size() != confirmItemMap.size()
                || receivedItems.stream().anyMatch(item -> !confirmItemMap.containsKey(item.getLineNo()))) {
            throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
        }

        int claimed = collaborationMapper.updateByIdAndBusinessStatus(collaboration.getId(),
                "PENDING_CONFIRMATION", new ErpWmsCollaborationBDO().setBusinessStatus("CONFIRMING"));
        if (claimed == 0) {
            throw exception(COLLABORATION_B_STATE_INVALID);
        }

        ErpPurchaseOrderSaveReqVO purchaseRequest = new ErpPurchaseOrderSaveReqVO()
                .setSupplierId(request.getSupplierId()).setAccountId(request.getAccountId())
                .setOrderTime(request.getOrderTime()).setDiscountPercent(BigDecimal.ZERO)
                .setDepositPrice(BigDecimal.ZERO).setRemark(request.getRemark());
        purchaseRequest.setItems(receivedItems.stream().map(receivedItem -> {
            ErpProductDO product = productMapper.selectByMaterialCode(receivedItem.getMaterialCode());
            if (product == null) {
                throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
            }
            ErpWmsCollaborationBConfirmReqVO.Item confirmItem = confirmItemMap.get(receivedItem.getLineNo());
            return new ErpPurchaseOrderSaveReqVO.Item().setProductId(product.getId())
                    .setProductUnitId(product.getUnitId()).setProductPrice(confirmItem.getProductPrice())
                    .setCount(receivedItem.getShortageQuantity()).setTaxPercent(confirmItem.getTaxPercent())
                    .setRemark(confirmItem.getRemark());
        }).toList());

        Long purchaseOrderId = purchaseOrderService.createPurchaseOrder(purchaseRequest);
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.getPurchaseOrder(purchaseOrderId);
        collaborationMapper.updateById(new ErpWmsCollaborationBDO().setId(collaboration.getId())
                .setBusinessStatus("ACCEPTED").setPurchaseOrderId(purchaseOrderId)
                .setPurchaseOrderNo(purchaseOrder.getNo()).setReason(request.getReason()));
        collaboration.setBusinessStatus("ACCEPTED").setPurchaseOrderId(purchaseOrderId)
                .setPurchaseOrderNo(purchaseOrder.getNo()).setReason(request.getReason());
        return buildConfirmResponse(collaboration);
    }

    private ErpCollaborationBReceiveRespDTO buildResponse(ErpCollaborationBReceiveReqDTO request,
                                                          ErpWmsCollaborationBDO collaboration) {
        return new ErpCollaborationBReceiveRespDTO().setAccepted(true).setRequestId(request.getRequestId())
                .setCollaborationNo(collaboration.getCollaborationNo())
                .setTechnicalStatus(collaboration.getTechnicalStatus())
                .setBusinessStatus(collaboration.getBusinessStatus())
                .setProcessedEventVersion(collaboration.getEventVersion());
    }

    private ErpWmsCollaborationBConfirmRespVO buildConfirmResponse(ErpWmsCollaborationBDO collaboration) {
        return new ErpWmsCollaborationBConfirmRespVO().setCollaborationId(collaboration.getId())
                .setCollaborationNo(collaboration.getCollaborationNo())
                .setBusinessStatus(collaboration.getBusinessStatus())
                .setPurchaseOrderId(collaboration.getPurchaseOrderId())
                .setPurchaseOrderNo(collaboration.getPurchaseOrderNo());
    }
}
