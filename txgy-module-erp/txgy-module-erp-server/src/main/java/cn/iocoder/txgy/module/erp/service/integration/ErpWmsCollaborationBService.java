package cn.iocoder.txgy.module.erp.service.integration;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveRespDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBConfirmRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.integration.vo.ErpWmsCollaborationBDetailRespVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpCollaborationConfirmLogDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.integration.ErpWmsCollaborationBItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpCollaborationConfirmLogMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.integration.ErpWmsCollaborationBMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.product.ErpProductMapper;
import cn.iocoder.txgy.module.erp.service.purchase.ErpPurchaseOrderService;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderResumeReqDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_ALREADY_CONFIRMED;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_CONFIRM_ITEM_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_NOT_EXISTS;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_STATE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.COLLABORATION_B_TYPE_INVALID;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.WMS_CALLBACK_STATE_STALE;

@Slf4j
@Service
public class ErpWmsCollaborationBService {

    /** 供应链协同单支持的类型集合（v0.1 规格 §4） */
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "PURCHASE_COLLABORATION", "SALE_COLLABORATION", "AWARD_COLLABORATION", "SHORTAGE_COLLABORATION");
    public static final String DEFAULT_TYPE = "SHORTAGE_COLLABORATION";

    /** 业务状态：待业务确认 */
    public static final String STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    /** 业务状态：待补充资料 */
    public static final String STATUS_NEED_SUPPLEMENT = "NEED_SUPPLEMENT";
    /** 业务状态：订单生成中（事务内中间态） */
    public static final String STATUS_CONFIRMING = "CONFIRMING";
    /** 业务状态：已接受（采购订单草稿已生成） */
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    /** 业务状态：已拒绝 */
    public static final String STATUS_REJECTED = "REJECTED";

    @Resource
    private ErpWmsCollaborationBMapper collaborationMapper;
    @Resource
    private ErpWmsCollaborationBItemMapper collaborationItemMapper;
    @Resource
    private ErpCollaborationConfirmLogMapper confirmLogMapper;
    @Resource
    private ErpProductMapper productMapper;
    @Autowired
    @Qualifier("erpWmsIntegrationRecordService")
    private ErpWmsIntegrationRecordService recordService;
    @Resource
    private ErpPurchaseOrderService purchaseOrderService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private ErpMesIntegrationGateway mesGateway;

    /**
     * 技术接收供应链协同单：校验幂等、事件版本、时间窗口与物料映射，落库后等待业务确认。
     * 技术接收成功仅代表 ERP 已保存协同单，不代表同意交易，更不生成正式订单。
     */
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
        String collaborationType = resolveCollaborationType(request.getCollaborationType());
        if (DEFAULT_TYPE.equals(collaborationType)
                && (request.getSourceProductionOrderNo() == null || request.getSourceProductionOrderNo().isBlank())) {
            throw new IllegalArgumentException("缺料采购协同单必须携带来源生产工单号");
        }
        for (ErpWmsDocumentItemDTO item : request.getItems()) {
            if (productMapper.selectByMaterialCode(item.getMaterialCode()) == null) {
                throw new IllegalStateException("统一物料编码不存在：" + item.getMaterialCode());
            }
        }
        ErpWmsCollaborationBDO collaboration = ErpWmsCollaborationBDO.builder()
                .collaborationNo(request.getCollaborationNo()).businessChainNo(request.getBusinessChainNo())
                .collaborationType(collaborationType)
                .sourceModule(request.getSourceModule()).sourceOrderNo(request.getSourceOrderNo())
                .sourceEnterpriseCode(request.getSourceEnterpriseCode())
                .targetEnterpriseCode(request.getTargetEnterpriseCode())
                .currency(request.getCurrency())
                .sourceProductionOrderNo(request.getSourceProductionOrderNo())
                .sourceCollaborationGNo(request.getSourceCollaborationGNo())
                .sourceSaleOrderNo(request.getSourceSaleOrderNo()).technicalStatus("RECEIVED")
                .businessStatus(STATUS_PENDING_CONFIRMATION).eventVersion(request.getEventVersion())
                .requiredAt(request.getRequiredAt()).build();
        collaborationMapper.insert(collaboration);
        for (ErpWmsDocumentItemDTO item : request.getItems()) {
            collaborationItemMapper.insert(ErpWmsCollaborationBItemDO.builder()
                    .collaborationId(collaboration.getId()).lineNo(item.getLineNo())
                    .sourceLineNo(item.getSourceLineNo())
                    .materialCode(item.getMaterialCode()).skuCode(item.getSkuCode())
                    .materialNameSnapshot(item.getMaterialNameSnapshot())
                    .specificationSnapshot(item.getSpecificationSnapshot())
                    .unitCode(item.getUnitCode()).unitNameSnapshot(item.getUnitNameSnapshot())
                    .shortageQuantity(item.getShortageQuantity() != null ? item.getShortageQuantity()
                            : item.getRequiredQuantity())
                    .referencePrice(item.getReferencePrice()).taxRate(item.getTaxRate())
                    .deliveryAddress(item.getDeliveryAddress()).build());
        }
        recordService.createInboundSuccess(request.getRequestId(), "COLLABORATION_B",
                request.getCollaborationNo(), request.getEventVersion(), request);
        return buildResponse(request, collaboration);
    }

    /**
     * 缺料采购入库完成后恢复对应的生产工单（设计 7.2）。
     * 反查协作单 B（按采购订单号）取得来源生产工单号，事务提交后异步通知 MES 重新领料。
     * 幂等：requestId = REC-{协作单号}，MES 侧按幂等键去重。
     */
    public void recoverWorkOrderAfterCommit(Long purchaseOrderId) {
        if (purchaseOrderId == null) return;
        List<ErpWmsCollaborationBDO> list = collaborationMapper.selectList(new LambdaQueryWrapperX<ErpWmsCollaborationBDO>()
                .eq(ErpWmsCollaborationBDO::getPurchaseOrderId, purchaseOrderId)
                .eq(ErpWmsCollaborationBDO::getCollaborationType, DEFAULT_TYPE));
        if (list.isEmpty()) return;
        ErpWmsCollaborationBDO cb = list.get(0);
        if (!STATUS_ACCEPTED.equals(cb.getBusinessStatus())) return;
        String workOrderCode = cb.getSourceProductionOrderNo();
        if (workOrderCode == null || workOrderCode.isBlank()) return;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String requestId = "REC-" + cb.getCollaborationNo();
        Long version = cb.getEventVersion() == null ? 1L : cb.getEventVersion() + 1;
        Runnable runner = () -> TenantUtils.execute(tenantId, () -> {
            try {
                MesWorkOrderResumeReqDTO resumeReq = new MesWorkOrderResumeReqDTO();
                resumeReq.setRequestId(requestId);
                resumeReq.setWorkOrderCode(workOrderCode);
                resumeReq.setTenantId(tenantId);
                resumeReq.setSourceModule("ERP");
                resumeReq.setOccurredAt(LocalDateTime.now());
                resumeReq.setDataVersion(version);
                resumeReq.setRemark("缺料采购入库完成，恢复生产工单领料");
                mesGateway.resumeWorkOrder(resumeReq);
            } catch (Exception ex) {
                log.warn("[recoverWorkOrderAfterCommit][协作单B({}) 恢复工单({})失败]", cb.getCollaborationNo(), workOrderCode, ex);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runner.run();
                }
            });
        } else {
            runner.run();
        }
    }

    /**
     * 业务确认与技术接收严格分离。接受时补齐供应商、价格等 ERP 商务信息，
     * 并生成采购订单草稿；拒绝或要求补充资料时仅流转协同单状态，不产生采购业务单据。
     * 支持动作：ACCEPTED / REJECTED / NEED_SUPPLEMENT。
     */
    @Transactional(rollbackFor = Exception.class)
    public ErpWmsCollaborationBConfirmRespVO confirm(ErpWmsCollaborationBConfirmReqVO request) {
        ErpWmsCollaborationBDO collaboration = collaborationMapper.selectById(request.getId());
        if (collaboration == null) {
            throw exception(COLLABORATION_B_NOT_EXISTS);
        }
        if (STATUS_ACCEPTED.equals(collaboration.getBusinessStatus())
                || STATUS_REJECTED.equals(collaboration.getBusinessStatus())) {
            throw exception(COLLABORATION_B_ALREADY_CONFIRMED);
        }
        if (!STATUS_PENDING_CONFIRMATION.equals(collaboration.getBusinessStatus())
                && !STATUS_NEED_SUPPLEMENT.equals(collaboration.getBusinessStatus())) {
            throw exception(COLLABORATION_B_STATE_INVALID);
        }

        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);
        if (!"ACCEPTED".equals(action) && !"REJECTED".equals(action) && !"NEED_SUPPLEMENT".equals(action)) {
            throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
        }
        if (!"ACCEPTED".equals(action) && (request.getReason() == null || request.getReason().isBlank())) {
            // 拒绝与要求补充资料必须说明原因
            throw exception(COLLABORATION_B_CONFIRM_ITEM_INVALID);
        }

        if ("NEED_SUPPLEMENT".equals(action)) {
            String targetStatus = STATUS_NEED_SUPPLEMENT;
            if (STATUS_NEED_SUPPLEMENT.equals(collaboration.getBusinessStatus())) {
                throw exception(COLLABORATION_B_STATE_INVALID);
            }
            int updated = claimCollaboration(collaboration, targetStatus, request.getReason());
            if (updated == 0) {
                throw exception(COLLABORATION_B_STATE_INVALID);
            }
            insertConfirmLog(collaboration, action, request.getReason());
            collaboration.setBusinessStatus(targetStatus).setReason(request.getReason());
            return buildConfirmResponse(collaboration);
        }
        if ("REJECTED".equals(action)) {
            int updated = claimCollaboration(collaboration, STATUS_REJECTED, request.getReason());
            if (updated == 0) {
                throw exception(COLLABORATION_B_STATE_INVALID);
            }
            insertConfirmLog(collaboration, action, request.getReason());
            collaboration.setBusinessStatus(STATUS_REJECTED).setReason(request.getReason());
            return buildConfirmResponse(collaboration);
        }

        // ACCEPTED：必须补齐供应商与采购时间等商务信息
        if (request.getSupplierId() == null || request.getOrderTime() == null || request.getItems() == null) {
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

        int claimed = claimCollaboration(collaboration, STATUS_CONFIRMING, request.getReason());
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
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        String loginUserName = resolveUserName(loginUserId);
        collaborationMapper.updateById(new ErpWmsCollaborationBDO().setId(collaboration.getId())
                .setBusinessStatus(STATUS_ACCEPTED).setPurchaseOrderId(purchaseOrderId)
                .setPurchaseOrderNo(purchaseOrder.getNo()).setReason(request.getReason())
                .setConfirmUserId(loginUserId).setConfirmUserName(loginUserName).setConfirmTime(LocalDateTime.now()));
        collaboration.setBusinessStatus(STATUS_ACCEPTED).setPurchaseOrderId(purchaseOrderId)
                .setPurchaseOrderNo(purchaseOrder.getNo()).setReason(request.getReason())
                .setConfirmUserId(loginUserId).setConfirmUserName(loginUserName).setConfirmTime(LocalDateTime.now());
        insertConfirmLog(collaboration, action, request.getReason());
        return buildConfirmResponse(collaboration);
    }

    public ErpWmsCollaborationBDetailRespVO getCollaborationDetail(Long id) {
        ErpWmsCollaborationBDO collaboration = collaborationMapper.selectById(id);
        if (collaboration == null) {
            throw exception(COLLABORATION_B_NOT_EXISTS);
        }
        return buildDetailResponse(collaboration);
    }

    public PageResult<ErpWmsCollaborationBDO> getCollaborationPage(ErpWmsCollaborationBPageReqVO pageReqVO) {
        return collaborationMapper.selectPage(pageReqVO);
    }

    private ErpWmsCollaborationBDetailRespVO buildDetailResponse(ErpWmsCollaborationBDO collaboration) {
        ErpWmsCollaborationBDetailRespVO detail = new ErpWmsCollaborationBDetailRespVO();
        detail.setId(collaboration.getId());
        detail.setCollaborationNo(collaboration.getCollaborationNo());
        detail.setCollaborationType(collaboration.getCollaborationType());
        detail.setBusinessChainNo(collaboration.getBusinessChainNo());
        detail.setSourceModule(collaboration.getSourceModule());
        detail.setSourceOrderNo(collaboration.getSourceOrderNo());
        detail.setSourceEnterpriseCode(collaboration.getSourceEnterpriseCode());
        detail.setTargetEnterpriseCode(collaboration.getTargetEnterpriseCode());
        detail.setCurrency(collaboration.getCurrency());
        detail.setSourceProductionOrderNo(collaboration.getSourceProductionOrderNo());
        detail.setSourceCollaborationGNo(collaboration.getSourceCollaborationGNo());
        detail.setSourceSaleOrderNo(collaboration.getSourceSaleOrderNo());
        detail.setTechnicalStatus(collaboration.getTechnicalStatus());
        detail.setBusinessStatus(collaboration.getBusinessStatus());
        detail.setEventVersion(collaboration.getEventVersion());
        detail.setRequiredAt(collaboration.getRequiredAt());
        detail.setReason(collaboration.getReason());
        detail.setConfirmUserId(collaboration.getConfirmUserId());
        detail.setConfirmUserName(collaboration.getConfirmUserName());
        detail.setConfirmTime(collaboration.getConfirmTime());
        detail.setPurchaseOrderId(collaboration.getPurchaseOrderId());
        detail.setPurchaseOrderNo(collaboration.getPurchaseOrderNo());
        // 订单状态快照实时查询，不复制 ERP 订单状态（v0.1 规格 §8.3）
        if (collaboration.getPurchaseOrderId() != null) {
            ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.getPurchaseOrder(collaboration.getPurchaseOrderId());
            if (purchaseOrder != null) {
                detail.setPurchaseOrderStatus(purchaseOrder.getStatus());
            }
        }
        detail.setItems(collaborationItemMapper.selectListByCollaborationId(collaboration.getId()).stream()
                .map(item -> {
                    ErpWmsCollaborationBDetailRespVO.Item itemVO = new ErpWmsCollaborationBDetailRespVO.Item();
                    itemVO.setLineNo(item.getLineNo());
                    itemVO.setSourceLineNo(item.getSourceLineNo());
                    itemVO.setMaterialCode(item.getMaterialCode());
                    itemVO.setSkuCode(item.getSkuCode());
                    itemVO.setMaterialNameSnapshot(item.getMaterialNameSnapshot());
                    itemVO.setSpecificationSnapshot(item.getSpecificationSnapshot());
                    itemVO.setUnitCode(item.getUnitCode());
                    itemVO.setUnitNameSnapshot(item.getUnitNameSnapshot());
                    itemVO.setShortageQuantity(item.getShortageQuantity());
                    itemVO.setReferencePrice(item.getReferencePrice());
                    itemVO.setTaxRate(item.getTaxRate());
                    itemVO.setDeliveryAddress(item.getDeliveryAddress());
                    return itemVO;
                }).toList());
        detail.setConfirmLogs(confirmLogMapper.selectListByCollaborationId(collaboration.getId()).stream()
                .map(log -> {
                    ErpWmsCollaborationBDetailRespVO.ConfirmLog logVO = new ErpWmsCollaborationBDetailRespVO.ConfirmLog();
                    logVO.setAction(log.getAction());
                    logVO.setReason(log.getReason());
                    logVO.setConfirmUserId(log.getConfirmUserId());
                    logVO.setConfirmUserName(log.getConfirmUserName());
                    logVO.setCreateTime(log.getCreateTime());
                    return logVO;
                }).toList());
        return detail;
    }

    private int claimCollaboration(ErpWmsCollaborationBDO collaboration, String targetStatus, String reason) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        String loginUserName = resolveUserName(loginUserId);
        int updated = collaborationMapper.updateByIdAndBusinessStatus(collaboration.getId(),
                collaboration.getBusinessStatus(), new ErpWmsCollaborationBDO()
                        .setBusinessStatus(targetStatus).setReason(reason)
                        .setConfirmUserId(loginUserId).setConfirmUserName(loginUserName)
                        .setConfirmTime(LocalDateTime.now()));
        return updated;
    }

    private void insertConfirmLog(ErpWmsCollaborationBDO collaboration, String action, String reason) {
        Long loginUserId = SecurityFrameworkUtils.getLoginUserId();
        confirmLogMapper.insert(ErpCollaborationConfirmLogDO.builder()
                .collaborationId(collaboration.getId())
                .collaborationNo(collaboration.getCollaborationNo())
                .action(action).reason(reason)
                .confirmUserId(loginUserId).confirmUserName(resolveUserName(loginUserId))
                .createTime(LocalDateTime.now()).build());
    }

    private String resolveUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getData();
            return user == null ? null : user.getNickname();
        } catch (Exception ignore) {
            return null;
        }
    }

    private String resolveCollaborationType(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_TYPE;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw exception(COLLABORATION_B_TYPE_INVALID);
        }
        return normalized;
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
