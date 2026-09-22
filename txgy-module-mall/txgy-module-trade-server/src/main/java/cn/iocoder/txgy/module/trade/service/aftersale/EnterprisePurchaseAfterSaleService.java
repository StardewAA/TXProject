package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.enums.aftersale.AfterSaleStatusEnum;
import cn.iocoder.txgy.module.trade.service.order.EnterprisePurchaseOrderService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.AFTER_SALE_ENTERPRISE_ACTION_REQUIRES_ORDER_CREATOR;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.AFTER_SALE_NOT_FOUND;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

/**
 * 企业采购售后边界。
 *
 * <p>同企业有效采购成员可以协作查看询价成交订单的售后记录；涉及退款事实或退货履约的申请、
 * 取消和填写退货物流，只允许订单实际下单人执行。底层售后单的 user_id 始终保留实际下单人，
 * 支付退款仍以原支付用户为准，不能被企业同事替换。售后 AOP 日志记录当前登录会员。</p>
 */
@Service
public class EnterprisePurchaseAfterSaleService {

    @Resource
    private EnterprisePurchaseOrderService enterpriseOrders;
    @Resource
    private TradeOrderQueryService orderQueryService;
    @Resource
    private AfterSaleMapper afterSaleMapper;
    @Resource
    private AfterSaleService afterSaleService;

    public PageResult<AfterSaleDO> getPage(Long actorMemberId, AppAfterSalePageReqVO reqVO) {
        Long enterpriseId = enterpriseOrders.requireEnterpriseId(actorMemberId);
        return afterSaleMapper.selectEnterpriseInquiryPage(reqVO,
                TenantContextHolder.getRequiredTenantId(), enterpriseId);
    }

    public AfterSaleAccess get(Long actorMemberId, Long afterSaleId) {
        AfterSaleDO afterSale = afterSaleId == null ? null : afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null || afterSale.getOrderId() == null) {
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        TradeOrderDO order = enterpriseOrders.getOrder(actorMemberId, afterSale.getOrderId());
        if (!Objects.equals(afterSale.getUserId(), order.getUserId())) {
            // 数据归属不一致时关闭访问，避免错误售后记录借企业订单扩大授权。
            throw exception(AFTER_SALE_NOT_FOUND);
        }
        return new AfterSaleAccess(afterSale, order,
                Objects.equals(order.getUserId(), actorMemberId));
    }

    public Long create(Long actorMemberId, AppAfterSaleCreateReqVO reqVO) {
        TradeOrderItemDO orderItem = reqVO.getOrderItemId() == null
                ? null : orderQueryService.getOrderItem(reqVO.getOrderItemId());
        if (orderItem == null || orderItem.getOrderId() == null) {
            throw exception(ORDER_NOT_FOUND);
        }
        TradeOrderDO order = enterpriseOrders.getOrder(actorMemberId, orderItem.getOrderId());
        if (!Objects.equals(orderItem.getUserId(), order.getUserId())) {
            throw exception(ORDER_NOT_FOUND);
        }
        requireActualOrderCreator(actorMemberId, order);
        // 必须传实际下单人，AfterSaleDO.userId 和后续退款用户均不能替换成企业同事。
        return afterSaleService.createAfterSale(order.getUserId(), reqVO);
    }

    public void delivery(Long actorMemberId, AppAfterSaleDeliveryReqVO reqVO) {
        AfterSaleAccess access = get(actorMemberId, reqVO.getId());
        requireActualOrderCreator(actorMemberId, access.order());
        afterSaleService.deliveryAfterSale(access.order().getUserId(), reqVO);
    }

    public void cancel(Long actorMemberId, Long afterSaleId) {
        AfterSaleAccess access = get(actorMemberId, afterSaleId);
        requireActualOrderCreator(actorMemberId, access.order());
        afterSaleService.cancelAfterSale(access.order().getUserId(), afterSaleId);
    }

    public Long getApplyingCount(Long actorMemberId) {
        Long enterpriseId = enterpriseOrders.requireEnterpriseId(actorMemberId);
        return afterSaleMapper.selectEnterpriseInquiryCount(TenantContextHolder.getRequiredTenantId(),
                enterpriseId, AfterSaleStatusEnum.APPLYING_STATUSES);
    }

    public Map<Long, AfterSaleDO> getLatest(Long actorMemberId, Collection<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty()) {
            return Map.of();
        }
        Long enterpriseId = enterpriseOrders.requireEnterpriseId(actorMemberId);
        Map<Long, AfterSaleDO> latest = new LinkedHashMap<>();
        for (AfterSaleDO afterSale : afterSaleMapper.selectEnterpriseLatestByOrderItemIds(
                TenantContextHolder.getRequiredTenantId(), enterpriseId, orderItemIds)) {
            latest.putIfAbsent(afterSale.getOrderItemId(), afterSale);
        }
        return latest;
    }

    private static void requireActualOrderCreator(Long actorMemberId, TradeOrderDO order) {
        if (!Objects.equals(actorMemberId, order.getUserId())) {
            throw exception(AFTER_SALE_ENTERPRISE_ACTION_REQUIRES_ORDER_CREATOR);
        }
    }

    public record AfterSaleAccess(AfterSaleDO afterSale, TradeOrderDO order,
                                  boolean createdByCurrentMember) {
    }
}
