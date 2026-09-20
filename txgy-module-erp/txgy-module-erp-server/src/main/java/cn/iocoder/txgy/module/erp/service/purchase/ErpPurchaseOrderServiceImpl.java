package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bpm.api.task.BpmProcessInstanceApi;
import cn.iocoder.txgy.module.bpm.api.task.dto.BpmProcessInstanceCreateReqDTO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.order.ErpPurchaseOrderSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpSupplierCreditStatusDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseOrderItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseOrderMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.service.approval.ErpApprovalNotificationService;
import cn.iocoder.txgy.module.erp.service.finance.ErpAccountService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.util.ErpAuditStatusUtils;
import cn.iocoder.txgy.module.erp.util.ErpBpmProcessUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.txgy.module.erp.enums.ErrorCodeConstants.*;

// TODO 芋艿：记录操作日志

/**
 * ERP 采购订单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpPurchaseOrderServiceImpl implements ErpPurchaseOrderService {

    /** 采购订单审批流程标识，可在 BPM 中动态配置任意数量的审批节点。 */
    public static final String BPM_PROCESS_DEFINITION_KEY = "erp-purchase-order-approval";

    @Resource
    private ErpPurchaseOrderMapper purchaseOrderMapper;
    @Resource
    private ErpPurchaseOrderItemMapper purchaseOrderItemMapper;

    @Resource
    private ErpNoRedisDAO noRedisDAO;

    @Resource
    private ErpProductService productService;
    @Resource
    private ErpSupplierService supplierService;
    @Resource
    private ErpSupplierCreditService supplierCreditService;
    @Resource
    private ErpAccountService accountService;
    @Resource
    private BpmProcessInstanceApi bpmProcessInstanceApi;
    @Resource
    private ErpApprovalNotificationService approvalNotificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseOrder(ErpPurchaseOrderSaveReqVO createReqVO) {
        // 1.1 校验订单项的有效性
        List<ErpPurchaseOrderItemDO> purchaseOrderItems = validatePurchaseOrderItems(createReqVO.getItems());
        // 1.2 校验供应商
        supplierService.validateSupplier(createReqVO.getSupplierId());
        ErpSupplierCreditStatusDO supplierCreditStatus =
                supplierCreditService.ensureStatus(createReqVO.getSupplierId());
        supplierCreditService.validateQualification(createReqVO.getSupplierId(),
                convertSet(purchaseOrderItems, ErpPurchaseOrderItemDO::getProductId));
        // 1.3 校验结算账户
        if (createReqVO.getAccountId() != null) {
            accountService.validateAccount(createReqVO.getAccountId());
        }
        // 1.4 生成订单号，并校验唯一性
        String no = noRedisDAO.generate(ErpNoRedisDAO.PURCHASE_ORDER_NO_PREFIX);
        if (purchaseOrderMapper.selectByNo(no) != null) {
            throw exception(PURCHASE_ORDER_NO_EXISTS);
        }

        // 2.1 插入订单
        ErpPurchaseOrderDO purchaseOrder = BeanUtils.toBean(createReqVO, ErpPurchaseOrderDO.class, in -> in
                .setNo(no).setStatus(ErpAuditStatus.DRAFT.getStatus())
                // 显式写入未删除，避免 bit(1) 字段在不同 MyBatis-Plus/JDBC 组合下被写成逻辑删除值。
                .setDeleted(false));
        applySupplierCreditSnapshot(purchaseOrder, supplierCreditStatus);
        calculateTotalPrice(purchaseOrder, purchaseOrderItems);
        purchaseOrderMapper.insert(purchaseOrder);
        // 2.2 插入订单项
        purchaseOrderItems.forEach(o -> o.setOrderId(purchaseOrder.getId()).setDeleted(false));
        purchaseOrderItemMapper.insertBatch(purchaseOrderItems);
        return purchaseOrder.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseOrder(ErpPurchaseOrderSaveReqVO updateReqVO) {
        // 1.1 校验存在
        ErpPurchaseOrderDO purchaseOrder = validatePurchaseOrderExists(updateReqVO.getId());
        if (ErpAuditStatus.APPROVE.getStatus().equals(purchaseOrder.getStatus())) {
            throw exception(PURCHASE_ORDER_UPDATE_FAIL_APPROVE, purchaseOrder.getNo());
        }
        if (ErpAuditStatus.PROCESS.getStatus().equals(purchaseOrder.getStatus())) {
            throw exception(PURCHASE_ORDER_UPDATE_FAIL_PROCESS, purchaseOrder.getNo());
        }
        // 1.2 校验供应商
        supplierService.validateSupplier(updateReqVO.getSupplierId());
        ErpSupplierCreditStatusDO supplierCreditStatus =
                supplierCreditService.ensureStatus(updateReqVO.getSupplierId());
        // 1.3 校验结算账户
        if (updateReqVO.getAccountId() != null) {
            accountService.validateAccount(updateReqVO.getAccountId());
        }
        // 1.4 校验订单项的有效性
        List<ErpPurchaseOrderItemDO> purchaseOrderItems = validatePurchaseOrderItems(updateReqVO.getItems());
        supplierCreditService.validateQualification(updateReqVO.getSupplierId(),
                convertSet(purchaseOrderItems, ErpPurchaseOrderItemDO::getProductId));

        // 2.1 更新订单
        ErpPurchaseOrderDO updateObj = BeanUtils.toBean(updateReqVO, ErpPurchaseOrderDO.class);
        applySupplierCreditSnapshot(updateObj, supplierCreditStatus);
        calculateTotalPrice(updateObj, purchaseOrderItems);
        purchaseOrderMapper.updateById(updateObj);
        // 2.2 更新订单项
        updatePurchaseOrderItemList(updateReqVO.getId(), purchaseOrderItems);
    }

    private void calculateTotalPrice(ErpPurchaseOrderDO purchaseOrder, List<ErpPurchaseOrderItemDO> purchaseOrderItems) {
        purchaseOrder.setTotalCount(getSumValue(purchaseOrderItems, ErpPurchaseOrderItemDO::getCount, BigDecimal::add));
        purchaseOrder.setTotalProductPrice(getSumValue(purchaseOrderItems, ErpPurchaseOrderItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
        purchaseOrder.setTotalTaxPrice(getSumValue(purchaseOrderItems, ErpPurchaseOrderItemDO::getTaxPrice, BigDecimal::add, BigDecimal.ZERO));
        purchaseOrder.setTotalPrice(purchaseOrder.getTotalProductPrice().add(purchaseOrder.getTotalTaxPrice()));
        // 计算优惠价格
        if (purchaseOrder.getDiscountPercent() == null) {
            purchaseOrder.setDiscountPercent(BigDecimal.ZERO);
        }
        purchaseOrder.setDiscountPrice(MoneyUtils.priceMultiplyPercent(purchaseOrder.getTotalPrice(), purchaseOrder.getDiscountPercent()));
        purchaseOrder.setTotalPrice(purchaseOrder.getTotalPrice().subtract(purchaseOrder.getDiscountPrice()));
    }

    private void applySupplierCreditSnapshot(ErpPurchaseOrderDO purchaseOrder,
                                             ErpSupplierCreditStatusDO creditStatus) {
        purchaseOrder.setSupplierCreditSnapshotVersion(creditStatus.getSnapshotVersion())
                .setSupplierCreditAuthStatus(creditStatus.getAuthStatus())
                .setSupplierCreditQualificationStatus(creditStatus.getQualificationStatus())
                .setSupplierCreditRiskLevel(creditStatus.getRiskLevel())
                .setSupplierCreditPurchaseLimitStatus(creditStatus.getPurchaseLimitStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseApprovePurchaseOrder(Long id, Long userId, String reason) {
        // 1.1 校验存在
        ErpPurchaseOrderDO purchaseOrder = validatePurchaseOrderExists(id);
        // 1.2 只有审批中(10)、已审批(20) 才能反审批。采购订单走 BPM 审批，不允许手工直改“已审批”
        Integer status = purchaseOrder.getStatus();
        if (!ErpAuditStatus.PROCESS.getStatus().equals(status)
                && !ErpAuditStatus.APPROVE.getStatus().equals(status)) {
            throw exception(PURCHASE_ORDER_REVERSE_APPROVE_FAIL_STATUS);
        }
        // 1.3 存在采购入库单，无法反审批
        if (purchaseOrder.getInCount().compareTo(BigDecimal.ZERO) > 0) {
            throw exception(PURCHASE_ORDER_PROCESS_FAIL_EXISTS_IN);
        }
        // 1.4 存在采购退货单，无法反审批
        if (purchaseOrder.getReturnCount().compareTo(BigDecimal.ZERO) > 0) {
            throw exception(PURCHASE_ORDER_PROCESS_FAIL_EXISTS_RETURN);
        }

        // 2. 乐观锁回退草稿，并清空 BPM 流程实例编号
        String processInstanceId = purchaseOrder.getProcessInstanceId();
        int updateCount = purchaseOrderMapper.reverseApproveByIdAndStatus(id, status, reason);
        if (updateCount == 0) {
            throw exception(PURCHASE_ORDER_REVERSE_APPROVE_FAIL_STATUS);
        }

        // 3. 事务提交后联动作废在途 BPM 流程实例（已结束的实例自动跳过）
        ErpBpmProcessUtils.cancelProcessInstanceAfterCommit(bpmProcessInstanceApi, userId,
                processInstanceId, "采购订单(" + purchaseOrder.getNo() + ")反审批：" + reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitPurchaseOrder(Long id, Long userId) {
        ErpPurchaseOrderDO purchaseOrder = validatePurchaseOrderExists(id);
        supplierCreditService.validateQualification(purchaseOrder.getSupplierId(),
                convertSet(purchaseOrderItemMapper.selectListByOrderId(id), ErpPurchaseOrderItemDO::getProductId));
        if (!ObjectUtil.equal(purchaseOrder.getStatus(), ErpAuditStatus.DRAFT.getStatus())
                && !ObjectUtil.equal(purchaseOrder.getStatus(), ErpAuditStatus.REJECT.getStatus())
                && !ObjectUtil.equal(purchaseOrder.getStatus(), ErpAuditStatus.CANCEL.getStatus())) {
            throw exception(PURCHASE_ORDER_SUBMIT_FAIL_STATUS, purchaseOrder.getNo());
        }
        Map<String, Object> variables = Map.of(
                "orderType", "PURCHASE",
                "orderNo", purchaseOrder.getNo(),
                "totalPrice", purchaseOrder.getTotalPrice());
        String processInstanceId = bpmProcessInstanceApi.createProcessInstance(userId,
                new BpmProcessInstanceCreateReqDTO()
                        .setProcessDefinitionKey(BPM_PROCESS_DEFINITION_KEY)
                        .setBusinessKey(String.valueOf(id))
                        .setVariables(variables)).getCheckedData();
        purchaseOrderMapper.updateById(new ErpPurchaseOrderDO().setId(id)
                .setStatus(ErpAuditStatus.PROCESS.getStatus())
                .setProcessInstanceId(processInstanceId));
        approvalNotificationService.sendOrderSubmittedAfterCommit(userId, "PURCHASE", purchaseOrder.getNo(), id);
    }

    @Override
    public void updatePurchaseOrderAuditStatus(Long id, Integer bpmStatus) {
        ErpPurchaseOrderDO purchaseOrder = validatePurchaseOrderExists(id);
        if (!ObjectUtil.equal(purchaseOrder.getStatus(), ErpAuditStatus.PROCESS.getStatus())) {
            return;
        }
        purchaseOrderMapper.updateById(new ErpPurchaseOrderDO().setId(id)
                .setStatus(ErpAuditStatusUtils.convertBpmResultToAuditStatus(bpmStatus)));
    }

    private List<ErpPurchaseOrderItemDO> validatePurchaseOrderItems(List<ErpPurchaseOrderSaveReqVO.Item> list) {
        // 1. 校验产品存在
        List<ErpProductDO> productList = productService.validProductList(
                convertSet(list, ErpPurchaseOrderSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(productList, ErpProductDO::getId);
        // 2. 转化为 ErpPurchaseOrderItemDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, ErpPurchaseOrderItemDO.class, item -> {
            item.setProductUnitId(productMap.get(item.getProductId()).getUnitId());
            item.setTotalPrice(MoneyUtils.priceMultiply(item.getProductPrice(), item.getCount()));
            if (item.getTotalPrice() == null) {
                return;
            }
            if (item.getTaxPercent() != null) {
                item.setTaxPrice(MoneyUtils.priceMultiplyPercent(item.getTotalPrice(), item.getTaxPercent()));
            }
        }));
    }

    private void updatePurchaseOrderItemList(Long id, List<ErpPurchaseOrderItemDO> newList) {
        // 第一步，对比新老数据，获得添加、修改、删除的列表
        List<ErpPurchaseOrderItemDO> oldList = purchaseOrderItemMapper.selectListByOrderId(id);
        List<List<ErpPurchaseOrderItemDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));

        // 第二步，批量添加、修改、删除
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setOrderId(id));
            purchaseOrderItemMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            purchaseOrderItemMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            purchaseOrderItemMapper.deleteByIds(convertList(diffList.get(2), ErpPurchaseOrderItemDO::getId));
        }
    }

    @Override
    public void updatePurchaseOrderInCount(Long id, Map<Long, BigDecimal> inCountMap) {
        List<ErpPurchaseOrderItemDO> orderItems = purchaseOrderItemMapper.selectListByOrderId(id);
        // 1. 更新每个采购订单项
        orderItems.forEach(item -> {
            BigDecimal inCount = inCountMap.getOrDefault(item.getId(), BigDecimal.ZERO);
            if (item.getInCount().equals(inCount)) {
                return;
            }
            if (inCount.compareTo(item.getCount()) > 0) {
                throw exception(PURCHASE_ORDER_ITEM_IN_FAIL_PRODUCT_EXCEED,
                        productService.getProduct(item.getProductId()).getName(), item.getCount());
            }
            purchaseOrderItemMapper.updateById(new ErpPurchaseOrderItemDO().setId(item.getId()).setInCount(inCount));
        });
        // 2. 更新采购订单
        BigDecimal totalInCount = getSumValue(inCountMap.values(), value -> value, BigDecimal::add, BigDecimal.ZERO);
        purchaseOrderMapper.updateById(new ErpPurchaseOrderDO().setId(id).setInCount(totalInCount));
    }

    @Override
    public void updatePurchaseOrderReturnCount(Long orderId, Map<Long, BigDecimal> returnCountMap) {
        List<ErpPurchaseOrderItemDO> orderItems = purchaseOrderItemMapper.selectListByOrderId(orderId);
        // 1. 更新每个采购订单项
        orderItems.forEach(item -> {
            BigDecimal returnCount = returnCountMap.getOrDefault(item.getId(), BigDecimal.ZERO);
            if (item.getReturnCount().equals(returnCount)) {
                return;
            }
            if (returnCount.compareTo(item.getInCount()) > 0) {
                throw exception(PURCHASE_ORDER_ITEM_RETURN_FAIL_IN_EXCEED,
                        productService.getProduct(item.getProductId()).getName(), item.getInCount());
            }
            purchaseOrderItemMapper.updateById(new ErpPurchaseOrderItemDO().setId(item.getId()).setReturnCount(returnCount));
        });
        // 2. 更新采购订单
        BigDecimal totalReturnCount = getSumValue(returnCountMap.values(), value -> value, BigDecimal::add, BigDecimal.ZERO);
        purchaseOrderMapper.updateById(new ErpPurchaseOrderDO().setId(orderId).setReturnCount(totalReturnCount));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchaseOrder(List<Long> ids) {
        // 1. 校验不处于已审批
        List<ErpPurchaseOrderDO> purchaseOrders = purchaseOrderMapper.selectByIds(ids);
        if (CollUtil.isEmpty(purchaseOrders)) {
            return;
        }
        purchaseOrders.forEach(purchaseOrder -> {
            if (ErpAuditStatus.APPROVE.getStatus().equals(purchaseOrder.getStatus())
                    || ErpAuditStatus.PROCESS.getStatus().equals(purchaseOrder.getStatus())) {
                throw exception(PURCHASE_ORDER_DELETE_FAIL_APPROVE, purchaseOrder.getNo());
            }
        });

        // 2. 遍历删除，并记录操作日志
        purchaseOrders.forEach(purchaseOrder -> {
            // 2.1 删除订单
            purchaseOrderMapper.deleteById(purchaseOrder.getId());
            // 2.2 删除订单项
            purchaseOrderItemMapper.deleteByOrderId(purchaseOrder.getId());
        });
    }

    private ErpPurchaseOrderDO validatePurchaseOrderExists(Long id) {
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderMapper.selectById(id);
        if (purchaseOrder == null) {
            throw exception(PURCHASE_ORDER_NOT_EXISTS);
        }
        return purchaseOrder;
    }

    @Override
    public ErpPurchaseOrderDO getPurchaseOrder(Long id) {
        return purchaseOrderMapper.selectById(id);
    }

    @Override
    public ErpPurchaseOrderDO getPurchaseOrderByNo(String no) {
        return purchaseOrderMapper.selectByNo(no);
    }

    @Override
    public ErpPurchaseOrderDO validatePurchaseOrder(Long id) {
        ErpPurchaseOrderDO purchaseOrder = validatePurchaseOrderExists(id);
        if (ObjectUtil.notEqual(purchaseOrder.getStatus(), ErpAuditStatus.APPROVE.getStatus())) {
            throw exception(PURCHASE_ORDER_NOT_APPROVE);
        }
        return purchaseOrder;
    }

    @Override
    public PageResult<ErpPurchaseOrderDO> getPurchaseOrderPage(ErpPurchaseOrderPageReqVO pageReqVO) {
        return purchaseOrderMapper.selectPage(pageReqVO);
    }

    // ==================== 订单项 ====================

    @Override
    public List<ErpPurchaseOrderItemDO> getPurchaseOrderItemListByOrderId(Long orderId) {
        return purchaseOrderItemMapper.selectListByOrderId(orderId);
    }

    @Override
    public List<ErpPurchaseOrderItemDO> getPurchaseOrderItemListByOrderIds(Collection<Long> orderIds) {
        if (CollUtil.isEmpty(orderIds)) {
            return Collections.emptyList();
        }
        return purchaseOrderItemMapper.selectListByOrderIds(orderIds);
    }

}
