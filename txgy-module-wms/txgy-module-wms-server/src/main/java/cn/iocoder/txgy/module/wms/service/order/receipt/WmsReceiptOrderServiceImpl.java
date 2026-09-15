package cn.iocoder.txgy.module.wms.service.order.receipt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.detail.WmsReceiptOrderDetailSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.receipt.vo.order.WmsReceiptOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.receipt.WmsReceiptOrderDetailDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.receipt.WmsReceiptOrderMapper;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

/**
 * WMS 入库单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsReceiptOrderServiceImpl implements WmsReceiptOrderService {
    public static final String PROCESS_KEY = "wms_receipt_approval";

    @Resource
    private WmsReceiptOrderMapper receiptOrderMapper;
    @Resource
    private WmsReceiptOrderDetailService receiptOrderDetailService;
    @Resource
    private WmsWarehouseService warehouseService;
    @Resource
    private WmsMerchantService merchantService;
    @Resource
    private WmsInventoryService inventoryService;
    @Resource
    private WmsItemSkuService itemSkuService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createReceiptOrder(WmsReceiptOrderSaveReqVO createReqVO) {
        // 1. 校验入库单保存数据
        validateReceiptOrderSaveData(createReqVO);

        // 2.1 插入入库单
        WmsReceiptOrderDO order = BeanUtils.toBean(createReqVO, WmsReceiptOrderDO.class);
        order.setStatus(WmsOrderStatusEnum.PREPARE.getStatus()).setApprovalStatus(BpmProcessInstanceStatusEnum.NOT_START.getStatus()).setExecutionStatus(0);
        fillReceiptOrderTotal(order, createReqVO);
        receiptOrderMapper.insert(order);
        // 2.2 插入入库单明细
        receiptOrderDetailService.createReceiptOrderDetailList(order.getId(), createReqVO);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReceiptOrder(WmsReceiptOrderSaveReqVO updateReqVO) {
        // 1. 校验入库单保存数据
        WmsReceiptOrderDO current = validateReceiptOrderPrepare(updateReqVO.getId());
        validateApprovalEditable(current);
        protectSourceReceipt(current, updateReqVO);
        validateReceiptOrderSaveData(updateReqVO);

        // 2.1 更新入库单
        WmsReceiptOrderDO updateObj = BeanUtils.toBean(updateReqVO, WmsReceiptOrderDO.class)
                .setStatus(WmsOrderStatusEnum.PREPARE.getStatus());
        fillReceiptOrderTotal(updateObj, updateReqVO);
        int updateCount = receiptOrderMapper.updateByIdAndStatus(updateReqVO.getId(),
                WmsOrderStatusEnum.PREPARE.getStatus(), updateObj);
        if (updateCount == 0) {
            throw exception(RECEIPT_ORDER_STATUS_NOT_PREPARE);
        }
        // 2.2 更新入库单明细
        receiptOrderDetailService.updateReceiptOrderDetailList(updateReqVO.getId(), updateReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReceiptOrder(Long id) {
        // 1. 校验存在，且可删除
        WmsReceiptOrderDO order = validateReceiptOrderExists(id);
        validateApprovalEditable(order);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())
                && ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.CANCELED.getStatus())) {
            throw exception(RECEIPT_ORDER_STATUS_NOT_DELETABLE);
        }

        // 2.1 删除入库单
        receiptOrderMapper.deleteById(id);
        // 2.2 删除入库单明细
        receiptOrderDetailService.deleteReceiptOrderDetailListByOrderId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeReceiptOrder(Long id) {
        // 1.1 校验存在，且草稿
        WmsReceiptOrderDO order = validateReceiptOrderPrepare(id);
        if (ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) throw exception(RECEIPT_ORDER_APPROVAL_NOT_APPROVED);
        completeApprovedReceiptOrder(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitReceiptOrderApproval(Long userId, Long id) {
        WmsReceiptOrderDO order = validateReceiptOrderPrepare(id);
        validateApprovalEditable(order);
        List<WmsReceiptOrderDetailDO> details = receiptOrderDetailService.validateReceiptOrderDetailListExists(id);
        validateAcceptance(details);
        itemSkuService.validateItemSkuListItemEnabled(details.stream().map(WmsReceiptOrderDetailDO::getSkuId).toList());
        HashMap<String, Object> variables = new HashMap<>();
        variables.put("orderId", id);
        variables.put("orderNo", order.getNo());
        variables.put("warehouseId", order.getWarehouseId());
        variables.put("detailCount", details.size());
        String processId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setBusinessKey(String.valueOf(id)).setVariables(variables)).getCheckedData();
        receiptOrderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WmsReceiptOrderDO>()
                .eq(WmsReceiptOrderDO::getId, id).set(WmsReceiptOrderDO::getProcessInstanceId, processId)
                .set(WmsReceiptOrderDO::getApprovalStatus, BpmProcessInstanceStatusEnum.RUNNING.getStatus())
                .set(WmsReceiptOrderDO::getApprovalTime, null).set(WmsReceiptOrderDO::getApprovalReason, null));
        return processId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateReceiptOrderApprovalStatus(Long id, String processInstanceId, Integer status, String reason) {
        if (processInstanceId == null || processInstanceId.isBlank() || !BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) return;
        WmsReceiptOrderDO order = validateReceiptOrderExists(id);
        if (ObjectUtil.notEqual(order.getProcessInstanceId(), processInstanceId)
                || ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) return;
        int changed = receiptOrderMapper.updateApprovalResultIfRunning(id, processInstanceId,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus(), new WmsReceiptOrderDO()
                        .setApprovalStatus(status).setApprovalTime(LocalDateTime.now()).setApprovalReason(reason));
        if (changed > 0 && ObjectUtil.equal(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            completeApprovedReceiptOrder(order);
        }
    }

    private void completeApprovedReceiptOrder(WmsReceiptOrderDO order) {
        List<WmsReceiptOrderDetailDO> details = receiptOrderDetailService.validateReceiptOrderDetailListExists(order.getId());
        validateAcceptance(details);
        itemSkuService.validateItemSkuListItemEnabled(details.stream().map(WmsReceiptOrderDetailDO::getSkuId).toList());
        if (receiptOrderMapper.updateByIdAndStatus(order.getId(), WmsOrderStatusEnum.PREPARE.getStatus(),
                new WmsReceiptOrderDO().setStatus(WmsOrderStatusEnum.FINISHED.getStatus()).setExecutionStatus(1)
                        .setExecutionTime(LocalDateTime.now()).setExecutionError(null)) == 0) return;
        createInventory(order, details);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        eventPublisher.publishEvent(new cn.iocoder.txgy.module.wms.service.order.WmsWarehouseExecutionEvent("RECEIPT", order.getId(), true, null));
                    }
                });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelReceiptOrder(Long id) {
        // 1. 校验存在，且草稿
        validateApprovalEditable(validateReceiptOrderPrepare(id));

        // 2. 作废入库单
        if (receiptOrderMapper.updateByIdAndStatus(id, WmsOrderStatusEnum.PREPARE.getStatus(),
                new WmsReceiptOrderDO().setStatus(WmsOrderStatusEnum.CANCELED.getStatus())) == 0) {
            throw exception(RECEIPT_ORDER_STATUS_NOT_PREPARE);
        }
    }

    @Override
    public WmsReceiptOrderDO getReceiptOrder(Long id) {
        return receiptOrderMapper.selectById(id);
    }

    @Override
    public PageResult<WmsReceiptOrderDO> getReceiptOrderPage(WmsReceiptOrderPageReqVO pageReqVO) {
        return receiptOrderMapper.selectPage(pageReqVO);
    }

    @Override
    public long getReceiptOrderCountByMerchantId(Long merchantId) {
        return receiptOrderMapper.selectCountByMerchantId(merchantId);
    }

    @Override
    public long getReceiptOrderCountByWarehouseId(Long warehouseId) {
        return receiptOrderMapper.selectCountByWarehouseId(warehouseId);
    }

    private void validateReceiptOrderSaveData(WmsReceiptOrderSaveReqVO reqVO) {
        // 校验入库单号唯一
        validateReceiptOrderNoUnique(reqVO.getId(), reqVO.getNo());
        // 校验仓库存在
        warehouseService.validateWarehouseExists(reqVO.getWarehouseId());
        // 校验供应商类型
        if (reqVO.getMerchantId() != null) {
            merchantService.validateSupplierMerchantExists(reqVO.getMerchantId());
        }
    }

    private void validateReceiptOrderNoUnique(Long id, String no) {
        WmsReceiptOrderDO order = receiptOrderMapper.selectByNo(no);
        if (order == null) {
            return;
        }
        if (id == null || ObjectUtil.notEqual(order.getId(), id)) {
            throw exception(RECEIPT_ORDER_NO_DUPLICATE);
        }
    }

    private void fillReceiptOrderTotal(WmsReceiptOrderDO order, WmsReceiptOrderSaveReqVO reqVO) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalPrice = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(reqVO.getDetails())) {
            for (WmsReceiptOrderDetailSaveReqVO detail : reqVO.getDetails()) {
                if (detail.getQuantity() != null) {
                    totalQuantity = totalQuantity.add(detail.getQuantity());
                }
                BigDecimal detailTotalPrice = getDetailTotalPrice(detail.getQuantity(), detail.getPrice(),
                        detail.getTotalPrice());
                if (detailTotalPrice != null) {
                    totalPrice = totalPrice.add(detailTotalPrice);
                }
            }
        }
        order.setTotalQuantity(totalQuantity).setTotalPrice(totalPrice);
    }

    private static BigDecimal getDetailTotalPrice(BigDecimal quantity, BigDecimal price, BigDecimal totalPrice) {
        if (totalPrice != null) {
            return totalPrice;
        }
        return MoneyUtils.priceMultiply(price, quantity);
    }

    private WmsReceiptOrderDO validateReceiptOrderExists(Long id) {
        WmsReceiptOrderDO order = id == null ? null : receiptOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw exception(RECEIPT_ORDER_NOT_EXISTS);
        }
        return order;
    }

    /**
     * 校验入库单存在且为草稿状态
     *
     * @param id 入库单编号
     * @return 入库单
     */
    private WmsReceiptOrderDO validateReceiptOrderPrepare(Long id) {
        WmsReceiptOrderDO order = validateReceiptOrderExists(id);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())) {
            throw exception(RECEIPT_ORDER_STATUS_NOT_PREPARE);
        }
        return order;
    }

    private void validateApprovalEditable(WmsReceiptOrderDO order) {
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            throw exception(RECEIPT_ORDER_APPROVAL_RUNNING);
        }
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            throw exception(RECEIPT_ORDER_APPROVAL_NOT_EDITABLE);
        }
    }

    /**
     * 增加入库单对应库存。
     *
     * 入库在库存变更模型中使用正数数量。
     *
     * @param order 入库单
     * @param details 入库单明细列表
     */
    private void createInventory(WmsReceiptOrderDO order, List<WmsReceiptOrderDetailDO> details) {
        List<WmsInventoryChangeReqDTO.Item> items = convertList(details.stream().filter(detail -> detail.getQualifiedQuantity().signum() > 0).toList(),
                detail -> BeanUtils.toBean(detail, WmsInventoryChangeReqDTO.Item.class)
                        .setQuantity(detail.getQualifiedQuantity())
                        .setTotalPrice(MoneyUtils.priceMultiply(detail.getPrice(), detail.getQualifiedQuantity())));
        if (items.isEmpty()) return;
        inventoryService.changeInventory(new WmsInventoryChangeReqDTO()
                .setOrderId(order.getId()).setOrderNo(order.getNo())
                .setOrderType(WmsOrderTypeEnum.RECEIPT.getType()).setItems(items));
    }

    private void validateAcceptance(List<WmsReceiptOrderDetailDO> details) {
        for (WmsReceiptOrderDetailDO detail : details) {
            WmsReceiptOrderDetailServiceImpl.validateAcceptanceQuantities(detail, true);
            if (detail.getQualityRequiredSnapshot() == null) throw new IllegalArgumentException("质检要求快照缺失，请核实物料后重新创建入库申请");
            if (Boolean.TRUE.equals(detail.getQualityRequiredSnapshot()) && (detail.getQualityImages() == null
                    || detail.getQualityImages().stream().noneMatch(url -> url != null && !url.isBlank()))) {
                throw new IllegalArgumentException("需要质检的入库明细必须上传质检图片");
            }
        }
    }

    private void protectSourceReceipt(WmsReceiptOrderDO current, WmsReceiptOrderSaveReqVO req) {
        req.setSourceModule(current.getSourceModule()).setBusinessChainNo(current.getBusinessChainNo());
        if (current.getSourceModule() == null) return;
        if (!java.util.Objects.equals(current.getWarehouseId(), req.getWarehouseId())
                || !java.util.Objects.equals(current.getNo(), req.getNo())
                || !java.util.Objects.equals(current.getType(), req.getType())
                || !java.util.Objects.equals(current.getBizOrderNo(), req.getBizOrderNo())
                || !java.util.Objects.equals(current.getMerchantId(), req.getMerchantId())) {
            throw new IllegalArgumentException("来源入库单不得修改单号、类型、仓库或来源关联");
        }
        List<WmsReceiptOrderDetailDO> old = receiptOrderDetailService.getReceiptOrderDetailList(current.getId());
        if (req.getDetails() == null || old.size() != req.getDetails().size()) throw new IllegalArgumentException("来源明细不可增删");
        Map<Long, WmsReceiptOrderDetailDO> byId = old.stream().collect(Collectors.toMap(WmsReceiptOrderDetailDO::getId, detail -> detail));
        for (WmsReceiptOrderDetailSaveReqVO detail : req.getDetails()) {
            WmsReceiptOrderDetailDO original = byId.remove(detail.getId());
            if (original == null || !java.util.Objects.equals(original.getSkuId(), detail.getSkuId())
                    || detail.getQuantity() == null || original.getQuantity().compareTo(detail.getQuantity()) != 0
                    || !sameAmount(original.getPrice(), detail.getPrice()) || !sameAmount(original.getTotalPrice(), detail.getTotalPrice())) {
                throw new IllegalArgumentException("来源明细SKU、申请数量和金额不可修改");
            }
            detail.setSourceLineNo(original.getSourceLineNo()).setSourceSnapshotJson(original.getSourceSnapshotJson());
        }
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

}
