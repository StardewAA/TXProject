package cn.iocoder.txgy.module.wms.service.order.shipment;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.detail.WmsShipmentOrderDetailSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.shipment.vo.order.WmsShipmentOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDetailDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.md.item.WmsItemSkuService;
import cn.iocoder.txgy.module.wms.service.inventory.dto.WmsInventoryChangeReqDTO;
import cn.iocoder.txgy.module.wms.service.md.merchant.WmsMerchantService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehouseService;
import cn.iocoder.txgy.module.wms.service.md.warehouse.WmsWarehousePersonnelService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

/**
 * WMS 出库单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsShipmentOrderServiceImpl implements WmsShipmentOrderService {

    public static final String PROCESS_KEY = "wms_shipment_approval";

    @Resource
    private WmsShipmentOrderMapper shipmentOrderMapper;
    @Resource
    private WmsShipmentOrderDetailService shipmentOrderDetailService;
    @Resource
    private WmsWarehouseService warehouseService;
    @Resource
    private WmsWarehousePersonnelService warehousePersonnelService;
    @Resource
    private WmsMerchantService merchantService;
    @Resource
    private WmsInventoryService inventoryService;
    @Resource
    private WmsItemSkuService itemSkuService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private cn.iocoder.txgy.module.wms.service.integration.WmsShortageService shortageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createShipmentOrder(WmsShipmentOrderSaveReqVO createReqVO) {
        // 1. 校验出库单保存数据
        validateShipmentOrderSaveData(createReqVO);

        // 2.1 插入出库单
        WmsShipmentOrderDO order = BeanUtils.toBean(createReqVO, WmsShipmentOrderDO.class);
        order.setStatus(WmsOrderStatusEnum.PREPARE.getStatus())
                .setApprovalStatus(BpmProcessInstanceStatusEnum.NOT_START.getStatus());
        fillShipmentOrderTotal(order, createReqVO);
        shipmentOrderMapper.insert(order);
        // 2.2 插入出库单明细
        shipmentOrderDetailService.createShipmentOrderDetailList(order.getId(), createReqVO);
        if (order.getSourceModule() != null) shortageService.check(order.getId());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShipmentOrder(WmsShipmentOrderSaveReqVO updateReqVO) {
        // 1. 校验出库单保存数据
        WmsShipmentOrderDO current = validateShipmentOrderEditable(updateReqVO.getId());
        protectSourceShipment(current, updateReqVO);
        validateShipmentOrderSaveData(updateReqVO);

        // 2.1 更新出库单
        WmsShipmentOrderDO updateObj = BeanUtils.toBean(updateReqVO, WmsShipmentOrderDO.class)
                .setStatus(WmsOrderStatusEnum.PREPARE.getStatus());
        fillShipmentOrderTotal(updateObj, updateReqVO);
        int updateCount = shipmentOrderMapper.updateByIdAndStatus(updateReqVO.getId(),
                WmsOrderStatusEnum.PREPARE.getStatus(), updateObj);
        if (updateCount == 0) {
            throw exception(SHIPMENT_ORDER_STATUS_NOT_PREPARE);
        }
        // 2.2 更新出库单明细
        shipmentOrderDetailService.updateShipmentOrderDetailList(updateReqVO.getId(), updateReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteShipmentOrder(Long id) {
        // 1. 校验存在，且可删除
        WmsShipmentOrderDO order = validateShipmentOrderExists(id);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())
                && ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.CANCELED.getStatus())) {
            throw exception(SHIPMENT_ORDER_STATUS_NOT_DELETABLE);
        }
        validateApprovalCancellable(order);

        // 2.1 删除出库单
        if (order.getSourceModule() != null) shortageService.cancel(id);
        shipmentOrderMapper.deleteById(id);
        // 2.2 删除出库单明细
        shipmentOrderDetailService.deleteShipmentOrderDetailListByOrderId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeShipmentOrder(Long id) {
        // 1.1 校验存在，且草稿
        WmsShipmentOrderDO order = validateShipmentOrderPrepare(id);
        if (order.getSourceModule() != null && Integer.valueOf(2).equals(order.getExecutionStatus())) {
            throw exception(SHIPMENT_ORDER_APPROVAL_NOT_APPROVED);
        }
        if (ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            throw exception(SHIPMENT_ORDER_APPROVAL_NOT_APPROVED);
        }
        shipmentOrderDetailService.validateShipmentOrderDetailListExists(id);
        completeApprovedShipmentOrder(order);
    }

    private void completeApprovedShipmentOrder(WmsShipmentOrderDO order) {
        executeShipmentInventory(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class,
            noRollbackFor = cn.iocoder.txgy.module.wms.service.integration.WmsShortageService.ShortageBlockedException.class)
    public String submitShipmentOrderApproval(Long userId, Long id) {
        WmsShipmentOrderDO order = validateShipmentOrderPrepare(id);
        validateApprovalEditable(order);
        warehousePersonnelService.validateConfigured(order.getWarehouseId());
        warehousePersonnelService.validateMember(order.getWarehouseId(), userId);
        List<WmsShipmentOrderDetailDO> details = shipmentOrderDetailService
                .validateShipmentOrderDetailListExists(id);
        if (order.getSourceModule() != null && shortageService.check(id)) {
            throw new cn.iocoder.txgy.module.wms.service.integration.WmsShortageService.ShortageBlockedException();
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("orderId", order.getId());
        variables.put("orderNo", order.getNo());
        variables.put("warehouseId", order.getWarehouseId());
        variables.put("wmsWarehouseManagerUserId", warehousePersonnelService.getManagerUserId(order.getWarehouseId()));
        variables.put("merchantId", order.getMerchantId());
        variables.put("totalQuantity", order.getTotalQuantity());
        variables.put("totalPrice", order.getTotalPrice());
        variables.put("detailCount", details.size());
        String processInstanceId = processInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY)
                        .setBusinessKey(String.valueOf(id)).setVariables(variables)).getCheckedData();
        shipmentOrderMapper.updateById(new WmsShipmentOrderDO().setId(id)
                .setProcessInstanceId(processInstanceId)
                .setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus())
                .setApprovalTime(null).setApprovalReason(null));
        return processInstanceId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShipmentOrderApprovalStatus(Long id, String processInstanceId, Integer status, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) {
            return;
        }
        WmsShipmentOrderDO order = validateShipmentOrderExists(id);
        if (ObjectUtil.notEqual(order.getProcessInstanceId(), processInstanceId)
                || ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            return;
        }
        int updateCount = shipmentOrderMapper.updateApprovalResultIfRunning(id, processInstanceId,
                BpmProcessInstanceStatusEnum.RUNNING.getStatus(), new WmsShipmentOrderDO()
                        .setApprovalStatus(status).setApprovalTime(LocalDateTime.now()).setApprovalReason(reason));
        if (updateCount > 0 && ObjectUtil.equal(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            completeApprovedShipmentOrder(order);
        }
    }

    /**
     * 使用同一连接上的保存点，使库存、流水和执行状态共同提交或回滚。
     * 执行失败回滚到保存点后，外层事务保留审批结果及失败信息。
     */
    private void executeShipmentInventory(WmsShipmentOrderDO order) {
        try {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehaviorName("PROPAGATION_NESTED");
            template.executeWithoutResult(status -> {
                List<WmsShipmentOrderDetailDO> details = shipmentOrderDetailService
                        .validateShipmentOrderDetailListExists(order.getId());
                itemSkuService.validateItemSkuListItemEnabled(details.stream()
                        .map(WmsShipmentOrderDetailDO::getSkuId).toList());
                if (shipmentOrderMapper.updateByIdAndStatus(order.getId(), WmsOrderStatusEnum.PREPARE.getStatus(),
                    new WmsShipmentOrderDO().setStatus(WmsOrderStatusEnum.FINISHED.getStatus())
                            .setExecutionStatus(1).setExecutionTime(LocalDateTime.now()).setExecutionError(null)) == 0) {
                    throw exception(SHIPMENT_ORDER_STATUS_NOT_PREPARE);
                }
                changeInventory(order, details);
            });
        } catch (RuntimeException ex) {
            shipmentOrderMapper.updateByIdAndStatus(order.getId(), WmsOrderStatusEnum.PREPARE.getStatus(),
                    new WmsShipmentOrderDO().setExecutionStatus(2).setExecutionTime(LocalDateTime.now())
                            .setExecutionError(truncateExecutionError(ex)));
            if (order.getSourceModule() != null) shortageService.check(order.getId());
        }
    }

    private static String truncateExecutionError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShipmentOrder(Long id) {
        // 1. 校验存在，且草稿
        WmsShipmentOrderDO order = validateShipmentOrderPrepare(id);
        validateApprovalCancellable(order);

        // 2. 作废出库单
        if (order.getSourceModule() != null) shortageService.cancel(id);
        if (shipmentOrderMapper.updateByIdAndStatus(id, WmsOrderStatusEnum.PREPARE.getStatus(),
                new WmsShipmentOrderDO().setStatus(WmsOrderStatusEnum.CANCELED.getStatus())) == 0) {
            throw exception(SHIPMENT_ORDER_STATUS_NOT_PREPARE);
        }
    }

    @Override
    public WmsShipmentOrderDO getShipmentOrder(Long id) {
        return shipmentOrderMapper.selectById(id);
    }

    @Override
    public PageResult<WmsShipmentOrderDO> getShipmentOrderPage(WmsShipmentOrderPageReqVO pageReqVO) {
        return shipmentOrderMapper.selectPage(pageReqVO);
    }

    @Override
    public long getShipmentOrderCountByMerchantId(Long merchantId) {
        return shipmentOrderMapper.selectCountByMerchantId(merchantId);
    }

    @Override
    public long getShipmentOrderCountByWarehouseId(Long warehouseId) {
        return shipmentOrderMapper.selectCountByWarehouseId(warehouseId);
    }

    private void validateShipmentOrderSaveData(WmsShipmentOrderSaveReqVO reqVO) {
        // 校验出库单号唯一
        validateShipmentOrderNoUnique(reqVO.getId(), reqVO.getNo());
        // 校验仓库存在
        warehouseService.validateWarehouseExists(reqVO.getWarehouseId());
        // 校验客户类型
        if (reqVO.getMerchantId() != null) {
            merchantService.validateCustomerMerchantExists(reqVO.getMerchantId());
        }
    }

    private void protectSourceShipment(WmsShipmentOrderDO current, WmsShipmentOrderSaveReqVO req) {
        req.setSourceModule(current.getSourceModule()).setBusinessChainNo(current.getBusinessChainNo());
        if (current.getSourceModule() == null) return;
        if (!java.util.Objects.equals(current.getNo(), req.getNo())
                || !java.util.Objects.equals(current.getType(), req.getType())
                || !java.util.Objects.equals(current.getWarehouseId(), req.getWarehouseId())
                || !java.util.Objects.equals(current.getBizOrderNo(), req.getBizOrderNo())
                || !java.util.Objects.equals(current.getMerchantId(), req.getMerchantId())) {
            throw new IllegalArgumentException("来源出库单不得修改单号、类型、仓库及来源关联");
        }
        var old = shipmentOrderDetailService.getShipmentOrderDetailList(current.getId());
        if (req.getDetails() == null || old.size() != req.getDetails().size())
            throw new IllegalArgumentException("来源出库明细不可增删");
        var byId = old.stream().collect(java.util.stream.Collectors.toMap(WmsShipmentOrderDetailDO::getId, d -> d));
        for (var detail : req.getDetails()) {
            var original = byId.remove(detail.getId());
            if (original == null || !java.util.Objects.equals(original.getSkuId(), detail.getSkuId())
                    || detail.getQuantity() == null || original.getQuantity().compareTo(detail.getQuantity()) != 0
                    || !sameAmount(original.getPrice(), detail.getPrice())
                    || !sameAmount(original.getTotalPrice(), detail.getTotalPrice()))
                throw new IllegalArgumentException("来源出库SKU、数量及金额不可修改");
            detail.setSourceLineNo(original.getSourceLineNo()).setSourceSnapshotJson(original.getSourceSnapshotJson());
        }
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private void validateShipmentOrderNoUnique(Long id, String no) {
        WmsShipmentOrderDO order = shipmentOrderMapper.selectByNo(no);
        if (order == null) {
            return;
        }
        if (id == null || ObjectUtil.notEqual(order.getId(), id)) {
            throw exception(SHIPMENT_ORDER_NO_DUPLICATE);
        }
    }

    private void fillShipmentOrderTotal(WmsShipmentOrderDO order, WmsShipmentOrderSaveReqVO reqVO) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalPrice = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(reqVO.getDetails())) {
            for (WmsShipmentOrderDetailSaveReqVO detail : reqVO.getDetails()) {
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

    private WmsShipmentOrderDO validateShipmentOrderExists(Long id) {
        WmsShipmentOrderDO order = id == null ? null : shipmentOrderMapper.selectByIdForUpdate(id);
        if (order == null) {
            throw exception(SHIPMENT_ORDER_NOT_EXISTS);
        }
        return order;
    }

    /**
     * 校验出库单存在且为草稿状态
     *
     * @param id 出库单编号
     * @return 出库单
     */
    private WmsShipmentOrderDO validateShipmentOrderPrepare(Long id) {
        WmsShipmentOrderDO order = validateShipmentOrderExists(id);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())) {
            throw exception(SHIPMENT_ORDER_STATUS_NOT_PREPARE);
        }
        return order;
    }

    private WmsShipmentOrderDO validateShipmentOrderEditable(Long id) {
        WmsShipmentOrderDO order = validateShipmentOrderPrepare(id);
        validateApprovalEditable(order);
        return order;
    }

    private void validateApprovalEditable(WmsShipmentOrderDO order) {
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) {
            throw exception(SHIPMENT_ORDER_APPROVAL_RUNNING);
        }
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            throw exception(SHIPMENT_ORDER_APPROVAL_NOT_EDITABLE);
        }
    }

    private void validateApprovalCancellable(WmsShipmentOrderDO order) {
        if (order.getSourceModule() != null && Integer.valueOf(2).equals(order.getExecutionStatus())
                && ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) return;
        validateApprovalEditable(order);
    }

    /**
     * 扣减出库单对应库存
     *
     * @param order 出库单
     * @param details 出库单明细列表
     */
    private void changeInventory(WmsShipmentOrderDO order, List<WmsShipmentOrderDetailDO> details) {
        List<WmsInventoryChangeReqDTO.Item> items = convertList(details,
                detail -> BeanUtils.toBean(detail, WmsInventoryChangeReqDTO.Item.class)
                        .setQuantity(detail.getQuantity().negate())
                        .setTotalPrice(negate(getDetailTotalPrice(detail.getQuantity(), detail.getPrice(),
                                detail.getTotalPrice()))));
        inventoryService.changeInventory(new WmsInventoryChangeReqDTO()
                .setOrderId(order.getId()).setOrderNo(order.getNo())
                .setOrderType(WmsOrderTypeEnum.SHIPMENT.getType()).setItems(items));
    }

    private static BigDecimal negate(BigDecimal value) {
        return value == null ? null : value.negate();
    }

}
