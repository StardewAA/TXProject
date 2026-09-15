package cn.iocoder.txgy.module.wms.service.order.check;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.bpm.enums.task.BpmProcessInstanceStatusEnum;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.detail.WmsCheckOrderDetailSaveReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.order.WmsCheckOrderPageReqVO;
import cn.iocoder.txgy.module.wms.controller.admin.order.check.vo.order.WmsCheckOrderSaveReqVO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.check.WmsCheckOrderDO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.check.WmsCheckOrderDetailDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.check.WmsCheckOrderMapper;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderTypeEnum;
import cn.iocoder.txgy.module.wms.enums.order.WmsOrderStatusEnum;
import cn.iocoder.txgy.module.wms.service.inventory.WmsInventoryService;
import cn.iocoder.txgy.module.wms.service.inventory.dto.WmsInventoryCheckReqDTO;
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

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.*;

/**
 * WMS 盘库单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class WmsCheckOrderServiceImpl implements WmsCheckOrderService {
    public static final String PROCESS_KEY = "wms_check_approval";

    @Resource
    private WmsCheckOrderMapper checkOrderMapper;
    @Resource
    private WmsCheckOrderDetailService checkOrderDetailService;
    @Resource
    private WmsWarehouseService warehouseService;
    @Resource
    private WmsInventoryService inventoryService;
    @Resource
    private BpmProcessInstanceApi processInstanceApi;
    @Resource
    private WmsItemSkuService itemSkuService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCheckOrder(WmsCheckOrderSaveReqVO createReqVO) {
        // 1. 校验盘库单保存数据
        validateCheckOrderSaveData(createReqVO);

        // 2.1 插入盘库单
        WmsCheckOrderDO order = BeanUtils.toBean(createReqVO, WmsCheckOrderDO.class);
        order.setStatus(WmsOrderStatusEnum.PREPARE.getStatus()).setApprovalStatus(BpmProcessInstanceStatusEnum.NOT_START.getStatus()).setExecutionStatus(0);
        fillCheckOrderTotal(order, createReqVO);
        checkOrderMapper.insert(order);
        // 2.2 插入盘库单明细
        checkOrderDetailService.createCheckOrderDetailList(order.getId(), createReqVO);
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCheckOrder(WmsCheckOrderSaveReqVO updateReqVO) {
        // 1. 校验盘库单保存数据
        validateCheckOrderPrepare(updateReqVO.getId());
        validateCheckOrderSaveData(updateReqVO);

        // 2.1 更新盘库单
        WmsCheckOrderDO updateObj = BeanUtils.toBean(updateReqVO, WmsCheckOrderDO.class)
                .setStatus(WmsOrderStatusEnum.PREPARE.getStatus());
        fillCheckOrderTotal(updateObj, updateReqVO);
        int updateCount = checkOrderMapper.updateByIdAndStatus(updateReqVO.getId(),
                WmsOrderStatusEnum.PREPARE.getStatus(), updateObj);
        if (updateCount == 0) {
            throw exception(CHECK_ORDER_STATUS_NOT_PREPARE);
        }
        // 2.2 更新盘库单明细
        checkOrderDetailService.updateCheckOrderDetailList(updateReqVO.getId(), updateReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCheckOrder(Long id) {
        // 1. 校验存在，且可删除
        WmsCheckOrderDO order = validateCheckOrderExists(id);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())
                && ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.CANCELED.getStatus())) {
            throw exception(CHECK_ORDER_STATUS_NOT_DELETABLE);
        }

        // 2.1 删除盘库单
        checkOrderMapper.deleteById(id);
        // 2.2 删除盘库单明细
        checkOrderDetailService.deleteCheckOrderDetailListByOrderId(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeCheckOrder(Long id) {
        // 1.1 校验存在，且草稿
        WmsCheckOrderDO order = validateCheckOrderPrepare(id);
        if (ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) throw exception(CHECK_ORDER_APPROVAL_NOT_APPROVED);
        // 1.2 校验盘库单明细存在
        List<WmsCheckOrderDetailDO> details = checkOrderDetailService.validateCheckOrderDetailListExists(id);
        itemSkuService.validateItemSkuListItemEnabled(details.stream()
                .map(WmsCheckOrderDetailDO::getSkuId).toList());

        // 2. 完成盘库单
        if (checkOrderMapper.updateByIdAndStatus(id, WmsOrderStatusEnum.PREPARE.getStatus(),
                new WmsCheckOrderDO().setStatus(WmsOrderStatusEnum.FINISHED.getStatus())) == 0) {
            throw exception(CHECK_ORDER_STATUS_NOT_PREPARE);
        }

        // 3. 盘点库存
        inventoryService.checkInventory(buildCheckInventoryReqDTO(order, details));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitCheckOrderApproval(Long userId, Long id) {
        WmsCheckOrderDO order = validateCheckOrderPrepare(id); validateApprovalEditable(order);
        List<WmsCheckOrderDetailDO> details = checkOrderDetailService.validateCheckOrderDetailListExists(id);
        HashMap<String,Object> vars = new HashMap<>(); vars.put("orderId", id); vars.put("orderNo", order.getNo()); vars.put("warehouseId", order.getWarehouseId()); vars.put("detailCount", details.size());
        String processId = processInstanceApi.createProcessInstance(userId, new BpmProcessInstanceCreateReqDTO().setProcessDefinitionKey(PROCESS_KEY).setBusinessKey(String.valueOf(id)).setVariables(vars)).getCheckedData();
        checkOrderMapper.updateById(new WmsCheckOrderDO().setId(id).setProcessInstanceId(processId).setApprovalStatus(BpmProcessInstanceStatusEnum.RUNNING.getStatus()).setApprovalTime(null).setApprovalReason(null));
        return processId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCheckOrderApprovalStatus(Long id, String processInstanceId, Integer status, String reason) {
        if (!BpmProcessInstanceStatusEnum.isProcessEndStatus(status)) return;
        WmsCheckOrderDO order = validateCheckOrderExists(id);
        if (ObjectUtil.notEqual(order.getProcessInstanceId(), processInstanceId) || ObjectUtil.notEqual(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) return;
        int changed = checkOrderMapper.updateApprovalResultIfRunning(id, processInstanceId, BpmProcessInstanceStatusEnum.RUNNING.getStatus(), new WmsCheckOrderDO().setApprovalStatus(status).setApprovalTime(LocalDateTime.now()).setApprovalReason(reason));
        if (changed > 0 && ObjectUtil.equal(status, BpmProcessInstanceStatusEnum.APPROVE.getStatus())) {
            List<WmsCheckOrderDetailDO> details = checkOrderDetailService.validateCheckOrderDetailListExists(order.getId());
            if (checkOrderMapper.updateByIdAndStatus(order.getId(), WmsOrderStatusEnum.PREPARE.getStatus(), new WmsCheckOrderDO().setStatus(WmsOrderStatusEnum.FINISHED.getStatus()).setExecutionStatus(1).setExecutionTime(LocalDateTime.now()).setExecutionError(null)) > 0) {
                inventoryService.checkInventory(buildCheckInventoryReqDTO(order, details));
            }
        }
    }

    private void validateApprovalEditable(WmsCheckOrderDO order) {
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.RUNNING.getStatus())) throw exception(CHECK_ORDER_APPROVAL_RUNNING);
        if (ObjectUtil.equal(order.getApprovalStatus(), BpmProcessInstanceStatusEnum.APPROVE.getStatus())) throw exception(CHECK_ORDER_APPROVAL_NOT_EDITABLE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelCheckOrder(Long id) {
        // 1. 校验存在，且草稿
        validateCheckOrderPrepare(id);

        // 2. 作废盘库单
        if (checkOrderMapper.updateByIdAndStatus(id, WmsOrderStatusEnum.PREPARE.getStatus(),
                new WmsCheckOrderDO().setStatus(WmsOrderStatusEnum.CANCELED.getStatus())) == 0) {
            throw exception(CHECK_ORDER_STATUS_NOT_PREPARE);
        }
    }

    @Override
    public WmsCheckOrderDO getCheckOrder(Long id) {
        return checkOrderMapper.selectById(id);
    }

    @Override
    public PageResult<WmsCheckOrderDO> getCheckOrderPage(WmsCheckOrderPageReqVO pageReqVO) {
        return checkOrderMapper.selectPage(pageReqVO);
    }

    @Override
    public long getCheckOrderCountByWarehouseId(Long warehouseId) {
        return checkOrderMapper.selectCountByWarehouseId(warehouseId);
    }

    private void validateCheckOrderSaveData(WmsCheckOrderSaveReqVO reqVO) {
        // 校验盘库单号唯一
        validateCheckOrderNoUnique(reqVO.getId(), reqVO.getNo());
        // 校验仓库存在
        warehouseService.validateWarehouseExists(reqVO.getWarehouseId());
    }

    private void validateCheckOrderNoUnique(Long id, String no) {
        WmsCheckOrderDO order = checkOrderMapper.selectByNo(no);
        if (order == null) {
            return;
        }
        if (id == null || ObjectUtil.notEqual(order.getId(), id)) {
            throw exception(CHECK_ORDER_NO_DUPLICATE);
        }
    }

    private void fillCheckOrderTotal(WmsCheckOrderDO order, WmsCheckOrderSaveReqVO reqVO) {
        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal actualPrice = BigDecimal.ZERO;
        if (CollUtil.isNotEmpty(reqVO.getDetails())) {
            for (WmsCheckOrderDetailSaveReqVO detail : reqVO.getDetails()) {
                BigDecimal differenceQuantity = calculateDifferenceQuantity(detail.getQuantity(), detail.getCheckQuantity());
                totalQuantity = totalQuantity.add(differenceQuantity);
                if (detail.getPrice() == null) {
                    continue;
                }
                if (detail.getQuantity() != null) {
                    totalPrice = totalPrice.add(MoneyUtils.priceMultiply(detail.getPrice(), detail.getQuantity()));
                }
                if (detail.getCheckQuantity() != null) {
                    actualPrice = actualPrice.add(MoneyUtils.priceMultiply(detail.getPrice(), detail.getCheckQuantity()));
                }
            }
        }
        order.setTotalQuantity(totalQuantity).setTotalPrice(totalPrice).setActualPrice(actualPrice);
    }

    private BigDecimal calculateDifferenceQuantity(BigDecimal quantity, BigDecimal checkQuantity) {
        if (quantity == null || checkQuantity == null) {
            return BigDecimal.ZERO;
        }
        return checkQuantity.subtract(quantity);
    }

    private WmsCheckOrderDO validateCheckOrderExists(Long id) {
        WmsCheckOrderDO order = id == null ? null : checkOrderMapper.selectById(id);
        if (order == null) {
            throw exception(CHECK_ORDER_NOT_EXISTS);
        }
        return order;
    }

    /**
     * 校验盘库单存在且为草稿状态
     *
     * @param id 盘库单编号
     * @return 盘库单
     */
    private WmsCheckOrderDO validateCheckOrderPrepare(Long id) {
        WmsCheckOrderDO order = validateCheckOrderExists(id);
        if (ObjectUtil.notEqual(order.getStatus(), WmsOrderStatusEnum.PREPARE.getStatus())) {
            throw exception(CHECK_ORDER_STATUS_NOT_PREPARE);
        }
        return order;
    }

    private WmsInventoryCheckReqDTO buildCheckInventoryReqDTO(WmsCheckOrderDO order,
                                                             List<WmsCheckOrderDetailDO> details) {
        return new WmsInventoryCheckReqDTO()
                .setOrderId(order.getId()).setOrderNo(order.getNo())
                .setOrderType(WmsOrderTypeEnum.CHECK.getType())
                .setItems(BeanUtils.toBean(details, WmsInventoryCheckReqDTO.Item.class));
    }

}
