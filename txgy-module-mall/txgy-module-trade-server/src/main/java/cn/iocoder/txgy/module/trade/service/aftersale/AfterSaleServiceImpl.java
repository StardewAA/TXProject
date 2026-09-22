package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.enums.UserTypeEnum;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.ObjectUtils;
import cn.iocoder.txgy.module.pay.api.refund.PayRefundApi;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.txgy.module.pay.api.refund.dto.PayRefundRespDTO;
import cn.iocoder.txgy.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.txgy.module.promotion.api.combination.CombinationRecordApi;
import cn.iocoder.txgy.module.promotion.api.combination.dto.CombinationRecordRespDTO;
import cn.iocoder.txgy.module.product.api.shop.ProductShopApi;
import cn.iocoder.txgy.module.product.api.shop.dto.ProductShopRespDTO;
import cn.iocoder.txgy.module.promotion.enums.combination.CombinationRecordStatusEnum;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSaleDisagreeReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.controller.admin.aftersale.vo.AfterSaleRefuseReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.convert.aftersale.AfterSaleConvert;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.dal.redis.no.TradeNoRedisDAO;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleOperateTypeEnum;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleTypeEnum;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleWayEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderItemAfterSaleStatusEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.txgy.module.trade.framework.aftersale.core.annotations.AfterSaleLog;
import cn.iocoder.txgy.module.trade.framework.aftersale.core.utils.AfterSaleLogUtils;
import cn.iocoder.txgy.module.trade.framework.order.config.TradeOrderProperties;
import cn.iocoder.txgy.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.util.json.JsonUtils.toJsonString;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.*;

/**
 * 售后订单 Service 实现类
 *
 * @author OPENLAB BS
 */
@Slf4j
@Service
@Validated
public class AfterSaleServiceImpl implements AfterSaleService {

    @Resource
    private cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchJournal workbenchJournal;

    private void workbenchChanged(Long orderId) {
        if (workbenchJournal != null) workbenchJournal.changed(orderId);
    }
    @Resource
    private cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper settlementOrderLock;

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private DeliveryExpressService deliveryExpressService;

    @Resource
    private AfterSaleMapper tradeAfterSaleMapper;
    @Resource
    private TradeNoRedisDAO tradeNoRedisDAO;

    @Resource
    private PayRefundApi payRefundApi;
    @Resource
    private CombinationRecordApi combinationRecordApi;

    @Resource
    private ProductShopApi productShopApi;

    @Resource
    private TradeOrderProperties tradeOrderProperties;

    @Override
    public PageResult<AfterSaleDO> getAfterSalePage(AfterSalePageReqVO pageReqVO) {
        PageResult<AfterSaleDO> page = tradeAfterSaleMapper.selectPage(pageReqVO);
        enrichShopNames(page.getList());
        return page;
    }

    @Override
    public PageResult<AfterSaleDO> getAfterSalePage(Long userId, AppAfterSalePageReqVO pageReqVO) {
        PageResult<AfterSaleDO> page = tradeAfterSaleMapper.selectPage(userId, pageReqVO);
        enrichShopNames(page.getList());
        return page;
    }

    @Override
    public AfterSaleDO getAfterSale(Long userId, Long id) {
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectByIdAndUserId(id, userId);
        enrichShopName(afterSale);
        return afterSale;
    }

    @Override
    public AfterSaleDO getAfterSale(Long id) {
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(id);
        enrichShopName(afterSale);
        return afterSale;
    }

    @Override
    public Map<Long, AfterSaleDO> getLatestAfterSales(Long userId, Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AfterSaleDO> latest = new LinkedHashMap<>();
        for (AfterSaleDO afterSale : tradeAfterSaleMapper.selectLatestByOrderItemIds(userId, orderItemIds)) {
            latest.putIfAbsent(afterSale.getOrderItemId(), afterSale);
        }
        return latest;
    }

    /**
     * 售后表只保存 shop_id。响应前补齐名称，避免管理端把企业店铺显示成“店铺 1”。
     * 店铺不存在或店铺服务暂时不可用时保留可识别的降级文案，不影响售后列表查询。
     */
    private void enrichShopNames(List<AfterSaleDO> afterSales) {
        if (afterSales == null || afterSales.isEmpty()) {
            return;
        }
        Map<Long, String> names = new HashMap<>();
        for (AfterSaleDO afterSale : afterSales) {
            if (afterSale == null) {
                continue;
            }
            Long shopId = afterSale.getShopId();
            if (shopId == null || shopId == 0L) {
                afterSale.setShopName("平台自营");
                continue;
            }
            if (!names.containsKey(shopId)) {
                names.put(shopId, resolveShopName(shopId));
            }
            afterSale.setShopName(names.get(shopId));
        }
    }

    private void enrichShopName(AfterSaleDO afterSale) {
        if (afterSale == null) {
            return;
        }
        Long shopId = afterSale.getShopId();
        afterSale.setShopName(shopId == null || shopId == 0L ? "平台自营" : resolveShopName(shopId));
    }

    private String resolveShopName(Long shopId) {
        try {
            CommonResult<ProductShopRespDTO> result = productShopApi.getShop(shopId);
            ProductShopRespDTO shop = result == null ? null : result.getCheckedData();
            if (shop != null && shop.getName() != null && !shop.getName().isBlank()) {
                return shop.getName();
            }
        } catch (RuntimeException ex) {
            log.warn("[enrichShopName][shopId({}) 查询店铺失败]", shopId, ex);
        }
        return "店铺 " + shopId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.MEMBER_CREATE)
    public Long createAfterSale(Long userId, AppAfterSaleCreateReqVO createReqVO) {
        // 第一步，前置校验
        TradeOrderItemDO tradeOrderItem = validateOrderItemApplicable(userId, createReqVO);

        // Serialize new after-sale applications with settlement's final order check.
        // The tenant interceptor still applies; never lock an arbitrary client-supplied order.
        settlementOrderLock.selectOne(new cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX<TradeOrderDO>()
                .eq(TradeOrderDO::getId, tradeOrderItem.getOrderId()).last("FOR UPDATE"));
        tradeOrderItem = validateOrderItemApplicable(userId, createReqVO);

        // 第二步，存储售后订单
        AfterSaleDO afterSale = createAfterSale(createReqVO, tradeOrderItem);
        return afterSale.getId();
    }

    /**
     * 校验交易订单项是否可以申请售后
     *
     * @param userId      用户编号
     * @param createReqVO 售后创建信息
     * @return 交易订单项
     */
    private TradeOrderItemDO validateOrderItemApplicable(Long userId, AppAfterSaleCreateReqVO createReqVO) {
        // 校验订单项存在
        TradeOrderItemDO orderItem = tradeOrderQueryService.getOrderItem(userId, createReqVO.getOrderItemId());
        if (orderItem == null) {
            throw exception(ORDER_ITEM_NOT_FOUND);
        }
        // 已申请售后，不允许再发起售后申请
        if (!TradeOrderItemAfterSaleStatusEnum.isNone(orderItem.getAfterSaleStatus())) {
            throw exception(AFTER_SALE_CREATE_FAIL_ORDER_ITEM_APPLIED);
        }
        // 申请的退款金额，不能超过商品的价格
        if (createReqVO.getRefundPrice() > orderItem.getPayPrice()) {
            throw exception(AFTER_SALE_CREATE_FAIL_REFUND_PRICE_ERROR);
        }

        // 校验订单存在
        TradeOrderDO order = tradeOrderQueryService.getOrder(userId, orderItem.getOrderId());
        if (order == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        // TODO 芋艿：超过一定时间，不允许售后
        // 已取消，无法发起售后
        if (TradeOrderStatusEnum.isCanceled(order.getStatus())) {
            throw exception(AFTER_SALE_CREATE_FAIL_ORDER_STATUS_CANCELED);
        }
        // 未支付，无法发起售后
        if (!TradeOrderStatusEnum.havePaid(order.getStatus())) {
            throw exception(AFTER_SALE_CREATE_FAIL_ORDER_STATUS_NO_PAID);
        }
        // 如果是【退货退款】的情况，需要额外校验是否发货
        if (createReqVO.getWay().equals(AfterSaleWayEnum.RETURN_AND_REFUND.getWay())
                && !TradeOrderStatusEnum.haveDelivered(order.getStatus())) {
            throw exception(AFTER_SALE_CREATE_FAIL_ORDER_STATUS_NO_DELIVERED);
        }
        // 如果是拼团订单，则进行中不允许售后
        if (TradeOrderTypeEnum.isCombination(order.getType())) {
            CombinationRecordRespDTO combinationRecord = combinationRecordApi.getCombinationRecordByOrderId(
                    order.getUserId(), order.getId()).getCheckedData();
            if (combinationRecord != null && CombinationRecordStatusEnum.isInProgress(combinationRecord.getStatus())) {
                throw exception(AFTER_SALE_CREATE_FAIL_ORDER_STATUS_COMBINATION_IN_PROGRESS);
            }
        }
        return orderItem;
    }

    private AfterSaleDO createAfterSale(AppAfterSaleCreateReqVO createReqVO,
                                        TradeOrderItemDO orderItem) {
        // 创建售后单
        AfterSaleDO afterSale = AfterSaleConvert.INSTANCE.convert(createReqVO, orderItem);
        // 售后单保存订单项的店铺快照，列表和退款处理页无需再根据已变更的商品反查归属。
        // 兼容历史订单：早期订单项没有店铺快照，数据库 shop_id 又是非空字段。
        Long shopId = orderItem.getShopId() == null ? 0L : orderItem.getShopId();
        String shopName = StrUtil.isBlank(orderItem.getShopName())
                ? (shopId == 0L ? "平台自营" : "店铺 " + shopId)
                : orderItem.getShopName();
        afterSale.setShopId(shopId).setShopName(shopName);
        afterSale.setNo(tradeNoRedisDAO.generate(TradeNoRedisDAO.AFTER_SALE_NO_PREFIX));
        afterSale.setStatus(AfterSaleStatusEnum.APPLY.getStatus());
        // 标记是售中还是售后
        TradeOrderDO order = tradeOrderQueryService.getOrder(orderItem.getUserId(), orderItem.getOrderId());
        afterSale.setOrderNo(order.getNo()); // 记录 orderNo 订单流水，方便后续检索
        afterSale.setType(TradeOrderStatusEnum.isCompleted(order.getStatus())
                ? AfterSaleTypeEnum.AFTER_SALE.getType() : AfterSaleTypeEnum.IN_SALE.getType());
        tradeAfterSaleMapper.insert(afterSale);

        // 更新交易订单项的售后状态
        tradeOrderUpdateService.updateOrderItemWhenAfterSaleCreate(orderItem.getId(), afterSale.getId());
        if(workbenchJournal!=null)workbenchJournal.changed(afterSale.getOrderId(),cn.iocoder.txgy.module.trade.service.workbench.OrderWorkbenchModels.Action.DELIVERY);

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), null,
                AfterSaleStatusEnum.APPLY.getStatus());
        return afterSale;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.ADMIN_AGREE_APPLY)
    public void agreeAfterSale(Long userId, Long id) {
        // 校验售后单存在，并状态未审批
        AfterSaleDO afterSale = validateAfterSaleAuditable(id);

        // 更新售后单的状态
        // 情况一：退款：标记为 WAIT_REFUND 状态。后续等退款发起成功后，在标记为 COMPLETE 状态
        // 情况二：退货退款：需要等用户退货后，才能发起退款
        Integer newStatus = afterSale.getWay().equals(AfterSaleWayEnum.REFUND.getWay()) ?
                AfterSaleStatusEnum.WAIT_REFUND.getStatus() : AfterSaleStatusEnum.SELLER_AGREE.getStatus();
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.APPLY.getStatus(), new AfterSaleDO()
                .setStatus(newStatus).setAuditUserId(userId).setAuditTime(LocalDateTime.now()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(), newStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.ADMIN_DISAGREE_APPLY)
    public void disagreeAfterSale(Long userId, AfterSaleDisagreeReqVO auditReqVO) {
        // 校验售后单存在，并状态未审批
        AfterSaleDO afterSale = validateAfterSaleAuditable(auditReqVO.getId());

        // 更新售后单的状态
        Integer newStatus = AfterSaleStatusEnum.SELLER_DISAGREE.getStatus();
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.APPLY.getStatus(), new AfterSaleDO()
                .setStatus(newStatus).setAuditUserId(userId).setAuditTime(LocalDateTime.now())
                .setAuditReason(auditReqVO.getAuditReason()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(), newStatus);

        // 更新交易订单项的售后状态为【未申请】
        tradeOrderUpdateService.updateOrderItemWhenAfterSaleCancel(afterSale.getOrderItemId());
    }

    /**
     * 校验售后单是否可审批（同意售后、拒绝售后）
     *
     * @param id 售后编号
     * @return 售后单
     */
    private AfterSaleDO validateAfterSaleAuditable(Long id) {
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.APPLY.getStatus())) {
            throw exception(AFTER_SALE_AUDIT_FAIL_STATUS_NOT_APPLY);
        }
        return afterSale;
    }

    private void updateAfterSaleStatus(Long id, Integer status, AfterSaleDO updateObj) {
        int updateCount = tradeAfterSaleMapper.updateByIdAndStatus(id, status, updateObj);
        if (updateCount == 0) {
            throw exception(AFTER_SALE_UPDATE_STATUS_FAIL);
        }
        if (workbenchJournal != null) workbenchChanged(tradeAfterSaleMapper.selectById(id).getOrderId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.MEMBER_DELIVERY)
    public void deliveryAfterSale(Long userId, AppAfterSaleDeliveryReqVO deliveryReqVO) {
        // 校验售后单存在，并状态未退货
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectByIdAndUserId(deliveryReqVO.getId(), userId);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.SELLER_AGREE.getStatus())) {
            throw exception(AFTER_SALE_DELIVERY_FAIL_STATUS_NOT_SELLER_AGREE);
        }
        DeliveryExpressDO express = deliveryExpressService.validateDeliveryExpress(deliveryReqVO.getLogisticsId());

        // 更新售后单的物流信息
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.SELLER_AGREE.getStatus(), new AfterSaleDO()
                .setStatus(AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())
                .setLogisticsId(deliveryReqVO.getLogisticsId()).setLogisticsNo(deliveryReqVO.getLogisticsNo())
                .setDeliveryTime(LocalDateTime.now()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(),
                AfterSaleStatusEnum.BUYER_DELIVERY.getStatus(),
                MapUtil.<String, Object>builder().put("deliveryName", express.getName())
                        .put("logisticsNo", deliveryReqVO.getLogisticsNo()).build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.ADMIN_AGREE_RECEIVE)
    public void receiveAfterSale(Long userId, Long id) {
        // 校验售后单存在，并状态为已退货
        AfterSaleDO afterSale = validateAfterSaleReceivable(id);

        // 退货入库也属于 WMS 执行能力；接口未开放前不能把后台确认冒充为已入库。
        throw new cn.iocoder.txgy.framework.common.exception.ServiceException(400,
                "WMS 退货入库接口尚未开放，暂不能确认收货");
        /* WMS 退货入库接口开放并返回执行结果后，再推进售后状态。

        // 更新售后单的状态
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.BUYER_DELIVERY.getStatus(), new AfterSaleDO()
                .setStatus(AfterSaleStatusEnum.WAIT_REFUND.getStatus()).setReceiveTime(LocalDateTime.now()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(),
                AfterSaleStatusEnum.WAIT_REFUND.getStatus());
        */
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.ADMIN_DISAGREE_RECEIVE)
    public void refuseAfterSale(Long userId, AfterSaleRefuseReqVO refuseReqVO) {
        // 校验售后单存在，并状态为已退货
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(refuseReqVO.getId());
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())) {
            throw exception(AFTER_SALE_CONFIRM_FAIL_STATUS_NOT_BUYER_DELIVERY);
        }

        // 更新售后单的状态
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.BUYER_DELIVERY.getStatus(), new AfterSaleDO()
                .setStatus(AfterSaleStatusEnum.SELLER_REFUSE.getStatus()).setReceiveTime(LocalDateTime.now())
                .setReceiveReason(refuseReqVO.getRefuseMemo()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(),
                AfterSaleStatusEnum.SELLER_REFUSE.getStatus(),
                MapUtil.of("reason", refuseReqVO.getRefuseMemo()));

        // 更新交易订单项的售后状态为【未申请】
        tradeOrderUpdateService.updateOrderItemWhenAfterSaleCancel(afterSale.getOrderItemId());
    }

    /**
     * 校验售后单是否可收货，即处于买家已发货
     *
     * @param id 售后编号
     * @return 售后单
     */
    private AfterSaleDO validateAfterSaleReceivable(Long id) {
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())) {
            throw exception(AFTER_SALE_CONFIRM_FAIL_STATUS_NOT_BUYER_DELIVERY);
        }
        return afterSale;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.ADMIN_REFUND)
    public void refundAfterSale(Long userId, String userIp, Long id) {
        // 校验售后单的状态，并状态待退款
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.WAIT_REFUND.getStatus())) {
            throw exception(AFTER_SALE_REFUND_FAIL_STATUS_NOT_WAIT_REFUND);
        }

        Integer newStatus;
        if (ObjUtil.equals(afterSale.getRefundPrice(), 0)) {
            // 特殊：退款为 0 的订单，直接标记为完成（积分商城）。关联案例：https://t.zsxq.com/AQEvL
            completeAfterSaleRefund(afterSale);
            newStatus = AfterSaleStatusEnum.COMPLETE.getStatus();
        } else {
            // 已经存在退款单时，只同步支付结果，避免用户重复点击后再次创建退款单。
            Long payRefundId = afterSale.getPayRefundId();
            if (payRefundId == null) {
                payRefundId = createPayRefund(userIp, afterSale);
            }
            // 模拟支付、余额支付会同步返回成功。此时立即完成售后；其它渠道仍由异步通知完成。
            newStatus = syncAfterSaleRefundResult(afterSale, payRefundId)
                    ? AfterSaleStatusEnum.COMPLETE.getStatus() : afterSale.getStatus();
        }

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(), newStatus);
    }

    private Long createPayRefund(String userIp, AfterSaleDO afterSale) {
        // 创建退款单
        PayRefundCreateReqDTO createReqDTO = AfterSaleConvert.INSTANCE.convert(userIp, afterSale, tradeOrderProperties)
                .setUserId(afterSale.getUserId()).setUserType(UserTypeEnum.MEMBER.getValue())
                .setReason(StrUtil.format("退款【{}】", afterSale.getSpuName()));
        Long payRefundId = payRefundApi.createRefund(createReqDTO).getCheckedData();

        // 更新售后单的退款单号
        tradeAfterSaleMapper.updateById(new AfterSaleDO().setId(afterSale.getId()).setPayRefundId(payRefundId));
        workbenchChanged(afterSale.getOrderId());
        return payRefundId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.SYSTEM_REFUND_SUCCESS)
    public void updateAfterSaleRefunded(Long id, Long orderId, Long payRefundId) {
        // 1. 校验售后单的状态，并状态待退款
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectById(id);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        // 支付平台可能重复通知。已经完成且退款单一致时直接返回，保证回调幂等。
        if (ObjectUtil.equal(afterSale.getStatus(), AfterSaleStatusEnum.COMPLETE.getStatus())
                && ObjectUtil.equal(afterSale.getPayRefundId(), payRefundId)) {
            return;
        }
        if (ObjectUtil.notEqual(afterSale.getStatus(), AfterSaleStatusEnum.WAIT_REFUND.getStatus())) {
            throw exception(AFTER_SALE_REFUND_FAIL_STATUS_NOT_WAIT_REFUND);
        }

        // 2. 校验退款单
        PayRefundRespDTO payRefund = validatePayRefund(afterSale, payRefundId);

        // 3. 处理退款结果
        if (PayRefundStatusEnum.isSuccess(payRefund.getStatus())) {
            completeAfterSaleRefund(afterSale);
            // 【情况二：退款失败】
        } else if (PayRefundStatusEnum.isFailure(payRefund.getStatus())) {
            // 记录售后日志
            AfterSaleLogUtils.setAfterSaleOperateType(AfterSaleOperateTypeEnum.SYSTEM_REFUND_FAIL);
            AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(), afterSale.getStatus());
        }
    }

    /**
     * 同步支付服务中的退款结果。用于同步完成的退款渠道，以及修复通知暂时不可达时的状态滞后。
     */
    private boolean syncAfterSaleRefundResult(AfterSaleDO afterSale, Long payRefundId) {
        PayRefundRespDTO payRefund = payRefundApi.getRefund(payRefundId).getCheckedData();
        if (payRefund == null || (!PayRefundStatusEnum.isSuccess(payRefund.getStatus())
                && !PayRefundStatusEnum.isFailure(payRefund.getStatus()))) {
            return false;
        }
        validatePayRefund(afterSale, payRefundId);
        if (PayRefundStatusEnum.isSuccess(payRefund.getStatus())) {
            completeAfterSaleRefund(afterSale);
            return true;
        }
        AfterSaleLogUtils.setAfterSaleOperateType(AfterSaleOperateTypeEnum.SYSTEM_REFUND_FAIL);
        return false;
    }

    private void completeAfterSaleRefund(AfterSaleDO afterSale) {
        updateAfterSaleStatus(afterSale.getId(), AfterSaleStatusEnum.WAIT_REFUND.getStatus(), new AfterSaleDO()
                .setStatus(AfterSaleStatusEnum.COMPLETE.getStatus()).setRefundTime(LocalDateTime.now()));
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(),
                AfterSaleStatusEnum.COMPLETE.getStatus());
        tradeOrderUpdateService.updateOrderItemWhenAfterSaleSuccess(afterSale.getOrderItemId(), afterSale.getRefundPrice());
    }

    /**
     * 校验退款单的合法性
     *
     * @param afterSale 售后单
     * @param payRefundId 退款单编号
     * @return 退款单
     */
    private PayRefundRespDTO validatePayRefund(AfterSaleDO afterSale, Long payRefundId) {
        // 1. 校验退款单是否存在
        PayRefundRespDTO payRefund = payRefundApi.getRefund(payRefundId).getCheckedData();
        if (payRefund == null) {
            log.error("[validatePayRefund][afterSale({}) payRefund({}) 不存在，请进行处理！]", afterSale.getId(), payRefundId);
            throw exception(AFTER_SALE_REFUND_FAIL_REFUND_NOT_FOUND);
        }
        // 2.1 校验退款单无退款结果（成功、失败）
        if (!PayRefundStatusEnum.isSuccess(payRefund.getStatus())
            && !PayRefundStatusEnum.isFailure(payRefund.getStatus())) {
            log.error("[validatePayRefund][afterSale({}) payRefund({}) 无退款结果，请进行处理！payRefund 数据是：{}]",
                    afterSale.getId(), payRefundId, toJsonString(payRefund));
            throw exception(AFTER_SALE_REFUND_FAIL_REFUND_NOT_SUCCESS_OR_FAILURE);
        }
        // 2.2 校验退款金额一致
        if (ObjectUtil.notEqual(payRefund.getRefundPrice(), afterSale.getRefundPrice())) {
            log.error("[validatePayRefund][afterSale({}) payRefund({}) 退款金额不匹配，请进行处理！afterSale 数据是：{}，payRefund 数据是：{}]",
                    afterSale.getId(), payRefundId, toJsonString(afterSale), toJsonString(payRefund));
            throw exception(AFTER_SALE_REFUND_FAIL_REFUND_PRICE_NOT_MATCH);
        }
        // 2.3 校验退款订单匹配（二次）
        if (ObjectUtil.notEqual(payRefund.getMerchantRefundId(), afterSale.getId().toString())) {
            log.error("[validatePayRefund][afterSale({}) 退款单不匹配({})，请进行处理！payRefund 数据是：{}]",
                    afterSale.getId(), payRefundId, toJsonString(payRefund));
            throw exception(AFTER_SALE_REFUND_FAIL_REFUND_ORDER_ID_ERROR);
        }
        return payRefund;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AfterSaleLog(operateType = AfterSaleOperateTypeEnum.MEMBER_CANCEL)
    public void cancelAfterSale(Long userId, Long id) {
        // 校验售后单的状态，并状态待退款
        AfterSaleDO afterSale = tradeAfterSaleMapper.selectByIdAndUserId(id, userId);
        if (afterSale == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        if (!ObjectUtils.equalsAny(afterSale.getStatus(), AfterSaleStatusEnum.APPLY.getStatus(),
                AfterSaleStatusEnum.SELLER_AGREE.getStatus(),
                AfterSaleStatusEnum.BUYER_DELIVERY.getStatus())) {
            throw exception(AFTER_SALE_CANCEL_FAIL_STATUS_NOT_APPLY_OR_AGREE_OR_BUYER_DELIVERY);
        }

        // 更新售后单的状态为【已取消】
        updateAfterSaleStatus(afterSale.getId(), afterSale.getStatus(), new AfterSaleDO()
                .setStatus(AfterSaleStatusEnum.BUYER_CANCEL.getStatus()));

        // 记录售后日志
        AfterSaleLogUtils.setAfterSaleInfo(afterSale.getId(), afterSale.getStatus(),
                AfterSaleStatusEnum.BUYER_CANCEL.getStatus());

        // 更新交易订单项的售后状态为【未申请】
        tradeOrderUpdateService.updateOrderItemWhenAfterSaleCancel(afterSale.getOrderItemId());
    }

    @Override
    public Long getApplyingAfterSaleCount(Long userId) {
        return tradeAfterSaleMapper.selectCountByUserIdAndStatus(userId, AfterSaleStatusEnum.APPLYING_STATUSES);
    }

}
