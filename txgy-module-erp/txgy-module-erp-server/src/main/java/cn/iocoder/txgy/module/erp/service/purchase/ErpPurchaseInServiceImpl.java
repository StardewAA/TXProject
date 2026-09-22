package cn.iocoder.txgy.module.erp.service.purchase;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.number.MoneyUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.in.ErpPurchaseInPageReqVO;
import cn.iocoder.txgy.module.erp.controller.admin.purchase.vo.in.ErpPurchaseInSaveReqVO;
import cn.iocoder.txgy.module.erp.dal.dataobject.product.ErpProductDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseInDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseInItemDO;
import cn.iocoder.txgy.module.erp.dal.dataobject.purchase.ErpPurchaseOrderDO;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseInItemMapper;
import cn.iocoder.txgy.module.erp.dal.mysql.purchase.ErpPurchaseInMapper;
import cn.iocoder.txgy.module.erp.dal.redis.no.ErpNoRedisDAO;
import cn.iocoder.txgy.module.erp.enums.ErpAuditStatus;
import cn.iocoder.txgy.module.erp.enums.stock.ErpStockRecordBizTypeEnum;
import cn.iocoder.txgy.module.erp.service.finance.ErpAccountService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsBusinessDocumentService;
import cn.iocoder.txgy.module.erp.service.integration.ErpWmsCollaborationBService;
import cn.iocoder.txgy.module.erp.service.product.ErpProductService;
import cn.iocoder.txgy.module.erp.service.stock.ErpStockRecordService;
import cn.iocoder.txgy.module.erp.service.stock.bo.ErpStockRecordCreateReqBO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
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
 * ERP 采购入库 Service 实现类
 *
 * @author OPENLAB BS
 */
@Service
@Validated
public class ErpPurchaseInServiceImpl implements ErpPurchaseInService {

    @Value("${txgy.integration.wms.mode:mock}")
    private String wmsMode;

    /** 审批通过时是否自动推送采购入库单到 WMS；默认 true 兼容老用户，企业改为 false 后只能走按钮触发。 */
    @Value("${txgy.integration.wms.auto-push-on-approve:true}")
    private boolean autoPushOnApprove;

    @Resource
    private ErpPurchaseInMapper purchaseInMapper;
    @Resource
    private ErpPurchaseInItemMapper purchaseInItemMapper;

    @Resource
    private ErpNoRedisDAO noRedisDAO;

    @Resource
    private ErpProductService productService;
    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private ErpPurchaseOrderService purchaseOrderService;
    @Resource
    private ErpAccountService accountService;
    @Resource
    private ErpStockRecordService stockRecordService;
    @Resource
    private ErpWmsBusinessDocumentService wmsBusinessDocumentService;
    @Resource
    private ErpWmsCollaborationBService collaborationBService;

    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createPurchaseIn(ErpPurchaseInSaveReqVO createReqVO) {
        // 1.1 校验采购订单已审核
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.validatePurchaseOrder(createReqVO.getOrderId());
        // 1.2 校验入库项的有效性
        List<ErpPurchaseInItemDO> purchaseInItems = validatePurchaseInItems(createReqVO.getItems());
        // 1.3 校验结算账户
        accountService.validateAccount(createReqVO.getAccountId());
        // 1.4 生成入库单号，并校验唯一性
        String no = noRedisDAO.generate(ErpNoRedisDAO.PURCHASE_IN_NO_PREFIX);
        if (purchaseInMapper.selectByNo(no) != null) {
            throw exception(PURCHASE_IN_NO_EXISTS);
        }

        // 2.1 插入入库
        ErpPurchaseInDO purchaseIn = BeanUtils.toBean(createReqVO, ErpPurchaseInDO.class, in -> in
                .setNo(no).setStatus(ErpAuditStatus.PROCESS.getStatus()))
                .setOrderNo(purchaseOrder.getNo()).setSupplierId(purchaseOrder.getSupplierId())
                .setBusinessChainNo(resolveBusinessChainNo(purchaseOrder));
        calculateTotalPrice(purchaseIn, purchaseInItems);
        purchaseInMapper.insert(purchaseIn);
        // 2.2 插入入库项
        purchaseInItems.forEach(o -> o.setInId(purchaseIn.getId()));
        purchaseInItemMapper.insertBatch(purchaseInItems);

        // 3. 更新采购订单的入库数量
        updatePurchaseOrderInCount(createReqVO.getOrderId());
        return purchaseIn.getId();
    }

    /**
     * 解析业务链号，保证「询价 → 定标 → 采购订单 → 采购入库」全链路可溯源。
     *
     * <p>优先继承采购订单的链号（由定标确认生成订单时写入 {@code INQUIRY-<询价单号>}）；
     * 手工建单的订单没有链号，则以订单号生成 {@code PO-<订单号>}，
     * 保证同一订单下的多张入库单链号一致。</p>
     *
     * <p>为什么必须在这里赋值：{@code ErpWmsBusinessDocumentService} 推送 WMS 时
     * 会用单据自己的 {@code businessChainNo}，为空才会兜底成 {@code BC-<单号>}
     * ——那正是原实现「订单→入库」断链的原因。</p>
     */
    private String resolveBusinessChainNo(ErpPurchaseOrderDO purchaseOrder) {
        String chainNo = purchaseOrder.getBusinessChainNo();
        return (chainNo != null && !chainNo.isBlank()) ? chainNo : "PO-" + purchaseOrder.getNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseIn(ErpPurchaseInSaveReqVO updateReqVO) {
        // 1.1 校验存在
        ErpPurchaseInDO purchaseIn = validatePurchaseInExists(updateReqVO.getId());
        if (ErpAuditStatus.APPROVE.getStatus().equals(purchaseIn.getStatus())) {
            throw exception(PURCHASE_IN_UPDATE_FAIL_APPROVE, purchaseIn.getNo());
        }
        // 1.2 校验采购订单已审核
        ErpPurchaseOrderDO purchaseOrder = purchaseOrderService.validatePurchaseOrder(updateReqVO.getOrderId());
        // 1.3 校验结算账户
        accountService.validateAccount(updateReqVO.getAccountId());
        // 1.4 校验订单项的有效性
        List<ErpPurchaseInItemDO> purchaseInItems = validatePurchaseInItems(updateReqVO.getItems());

        // 2.1 更新入库
        ErpPurchaseInDO updateObj = BeanUtils.toBean(updateReqVO, ErpPurchaseInDO.class)
                .setOrderNo(purchaseOrder.getNo()).setSupplierId(purchaseOrder.getSupplierId());
        calculateTotalPrice(updateObj, purchaseInItems);
        purchaseInMapper.updateById(updateObj);
        // 2.2 更新入库项
        updatePurchaseInItemList(updateReqVO.getId(), purchaseInItems);

        // 3.1 更新采购订单的入库数量
        updatePurchaseOrderInCount(updateObj.getOrderId());
        // 3.2 注意：如果采购订单编号变更了，需要更新“老”采购订单的入库数量
        if (ObjectUtil.notEqual(purchaseIn.getOrderId(), updateObj.getOrderId())) {
            updatePurchaseOrderInCount(purchaseIn.getOrderId());
        }
    }

    private void calculateTotalPrice(ErpPurchaseInDO purchaseIn, List<ErpPurchaseInItemDO> purchaseInItems) {
        purchaseIn.setTotalCount(getSumValue(purchaseInItems, ErpPurchaseInItemDO::getCount, BigDecimal::add));
        purchaseIn.setTotalProductPrice(getSumValue(purchaseInItems, ErpPurchaseInItemDO::getTotalPrice, BigDecimal::add, BigDecimal.ZERO));
        purchaseIn.setTotalTaxPrice(getSumValue(purchaseInItems, ErpPurchaseInItemDO::getTaxPrice, BigDecimal::add, BigDecimal.ZERO));
        purchaseIn.setTotalPrice(purchaseIn.getTotalProductPrice().add(purchaseIn.getTotalTaxPrice()));
        // 计算优惠价格
        if (purchaseIn.getDiscountPercent() == null) {
            purchaseIn.setDiscountPercent(BigDecimal.ZERO);
        }
        purchaseIn.setDiscountPrice(MoneyUtils.priceMultiplyPercent(purchaseIn.getTotalPrice(), purchaseIn.getDiscountPercent()));
        purchaseIn.setTotalPrice(purchaseIn.getTotalPrice().subtract(purchaseIn.getDiscountPrice()).add(purchaseIn.getOtherPrice()));
    }

    private void updatePurchaseOrderInCount(Long orderId) {
        // 1.1 查询采购订单对应的采购入库单列表
        List<ErpPurchaseInDO> purchaseIns = purchaseInMapper.selectListByOrderId(orderId);
        // 1.2 查询对应的采购订单项的入库数量
        Map<Long, BigDecimal> returnCountMap = purchaseInItemMapper.selectOrderItemCountSumMapByInIds(
                convertList(purchaseIns, ErpPurchaseInDO::getId));
        // 2. 更新采购订单的入库数量
        purchaseOrderService.updatePurchaseOrderInCount(orderId, returnCountMap);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePurchaseInStatus(Long id, Integer status) {
        boolean approve = ErpAuditStatus.APPROVE.getStatus().equals(status);
        // 1.1 校验存在
        ErpPurchaseInDO purchaseIn = validatePurchaseInExists(id);
        // 1.2 校验状态
        if (purchaseIn.getStatus().equals(status)) {
            throw exception(approve ? PURCHASE_IN_APPROVE_FAIL : PURCHASE_IN_PROCESS_FAIL);
        }
        // 1.3 校验已付款
        if (!approve && purchaseIn.getPaymentPrice().compareTo(BigDecimal.ZERO) > 0) {
            throw exception(PURCHASE_IN_PROCESS_FAIL_EXISTS_PAYMENT);
        }

        // 2. 更新状态
        int updateCount = purchaseInMapper.updateByIdAndStatus(id, purchaseIn.getStatus(),
                new ErpPurchaseInDO().setStatus(status));
        if (updateCount == 0) {
            throw exception(approve ? PURCHASE_IN_APPROVE_FAIL : PURCHASE_IN_PROCESS_FAIL);
        }

        // 3. 变更库存
        List<ErpPurchaseInItemDO> purchaseInItems = purchaseInItemMapper.selectListByInId(id);
        Integer bizType = approve ? ErpStockRecordBizTypeEnum.PURCHASE_IN.getType()
                : ErpStockRecordBizTypeEnum.PURCHASE_IN_CANCEL.getType();
        // 说明：ERP 业务账始终由 ERP 单据驱动，不随 WMS 集成模式变化。
        // 原实现在 remote 模式下跳过本段，会让 ERP 业务账停止记录采购入库；
        // 而 WMS 回调侧并不写 ERP 库存（integration 包内无 createStockRecord 调用），
        // 故去掉守卫不会重复计数。实物账由 erp_stock_mirror 单独镜像 WMS。
        purchaseInItems.forEach(purchaseInItem -> {
            BigDecimal count = approve ? purchaseInItem.getCount() : purchaseInItem.getCount().negate();
            stockRecordService.createStockRecord(new ErpStockRecordCreateReqBO(
                    purchaseInItem.getProductId(), purchaseInItem.getWarehouseId(), count,
                    bizType, purchaseInItem.getInId(), purchaseInItem.getId(), purchaseIn.getNo()));
        });
        // 自动推送 WMS 由开关控制（团队 master 引入）：未开启时仅登记 ERP 业务账，不触发 WMS 正向入库
        if (approve && autoPushOnApprove) {
            wmsBusinessDocumentService.preparePurchaseReceiptAfterCommit(id);
            // 缺料工单恢复（设计 7.2）：采购入库后通知 MES 重新领料
            collaborationBService.recoverWorkOrderAfterCommit(purchaseIn.getOrderId());
        }
    }

    @Override
    public void updatePurchaseInPaymentPrice(Long id, BigDecimal paymentPrice) {
        ErpPurchaseInDO purchaseIn = purchaseInMapper.selectById(id);
        if (purchaseIn.getPaymentPrice().equals(paymentPrice)) {
            return;
        }
        if (paymentPrice.compareTo(purchaseIn.getTotalPrice()) > 0) {
            throw exception(PURCHASE_IN_FAIL_PAYMENT_PRICE_EXCEED, paymentPrice, purchaseIn.getTotalPrice());
        }
        purchaseInMapper.updateById(new ErpPurchaseInDO().setId(id).setPaymentPrice(paymentPrice));
    }

    private List<ErpPurchaseInItemDO> validatePurchaseInItems(List<ErpPurchaseInSaveReqVO.Item> list) {
        // 1. 校验产品存在
        List<ErpProductDO> productList = productService.validProductList(
                convertSet(list, ErpPurchaseInSaveReqVO.Item::getProductId));
        Map<Long, ErpProductDO> productMap = convertMap(productList, ErpProductDO::getId);
        // 2. 转化为 ErpPurchaseInItemDO 列表
        return convertList(list, o -> BeanUtils.toBean(o, ErpPurchaseInItemDO.class, item -> {
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

    private void updatePurchaseInItemList(Long id, List<ErpPurchaseInItemDO> newList) {
        // 第一步，对比新老数据，获得添加、修改、删除的列表
        List<ErpPurchaseInItemDO> oldList = purchaseInItemMapper.selectListByInId(id);
        List<List<ErpPurchaseInItemDO>> diffList = diffList(oldList, newList, // id 不同，就认为是不同的记录
                (oldVal, newVal) -> oldVal.getId().equals(newVal.getId()));

        // 第二步，批量添加、修改、删除
        if (CollUtil.isNotEmpty(diffList.get(0))) {
            diffList.get(0).forEach(o -> o.setInId(id));
            purchaseInItemMapper.insertBatch(diffList.get(0));
        }
        if (CollUtil.isNotEmpty(diffList.get(1))) {
            purchaseInItemMapper.updateBatch(diffList.get(1));
        }
        if (CollUtil.isNotEmpty(diffList.get(2))) {
            purchaseInItemMapper.deleteByIds(convertList(diffList.get(2), ErpPurchaseInItemDO::getId));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePurchaseIn(List<Long> ids) {
        // 1. 校验不处于已审批
        List<ErpPurchaseInDO> purchaseIns = purchaseInMapper.selectByIds(ids);
        if (CollUtil.isEmpty(purchaseIns)) {
            return;
        }
        purchaseIns.forEach(purchaseIn -> {
            if (ErpAuditStatus.APPROVE.getStatus().equals(purchaseIn.getStatus())) {
                throw exception(PURCHASE_IN_DELETE_FAIL_APPROVE, purchaseIn.getNo());
            }
        });

        // 2. 遍历删除，并记录操作日志
        purchaseIns.forEach(purchaseIn -> {
            // 2.1 删除订单
            purchaseInMapper.deleteById(purchaseIn.getId());
            // 2.2 删除订单项
            purchaseInItemMapper.deleteByInId(purchaseIn.getId());

            // 2.3 更新采购订单的入库数量
            updatePurchaseOrderInCount(purchaseIn.getOrderId());
        });

    }

    private ErpPurchaseInDO validatePurchaseInExists(Long id) {
        ErpPurchaseInDO purchaseIn = purchaseInMapper.selectById(id);
        if (purchaseIn == null) {
            throw exception(PURCHASE_IN_NOT_EXISTS);
        }
        return purchaseIn;
    }

    @Override
    public ErpPurchaseInDO getPurchaseIn(Long id) {
        return purchaseInMapper.selectById(id);
    }

    @Override
    public ErpPurchaseInDO validatePurchaseIn(Long id) {
        ErpPurchaseInDO purchaseIn = validatePurchaseInExists(id);
        if (ObjectUtil.notEqual(purchaseIn.getStatus(), ErpAuditStatus.APPROVE.getStatus())) {
            throw exception(PURCHASE_IN_NOT_APPROVE);
        }
        return purchaseIn;
    }

    @Override
    public PageResult<ErpPurchaseInDO> getPurchaseInPage(ErpPurchaseInPageReqVO pageReqVO) {
        return purchaseInMapper.selectPage(pageReqVO);
    }

    // ==================== 采购入库项 ====================

    @Override
    public List<ErpPurchaseInItemDO> getPurchaseInItemListByInId(Long inId) {
        return purchaseInItemMapper.selectListByInId(inId);
    }

    @Override
    public List<ErpPurchaseInItemDO> getPurchaseInItemListByInIds(Collection<Long> inIds) {
        if (CollUtil.isEmpty(inIds)) {
            return Collections.emptyList();
        }
        return purchaseInItemMapper.selectListByInIds(inIds);
    }

}
