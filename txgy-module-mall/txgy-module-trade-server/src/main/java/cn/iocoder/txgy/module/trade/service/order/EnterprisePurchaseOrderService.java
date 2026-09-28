package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.product.api.enterprise.ProductPurchaseMemberApi;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderUpdateAddressReqVO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderLogisticsRespVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.txgy.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.txgy.module.trade.service.delivery.TradeOrderLogisticsService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

/**
 * 企业采购订单边界。只有当前租户的有效采购成员，才可访问本企业的询价成交订单。
 * 普通商城订单始终留在个人订单域，不因企业成员身份而扩大读取范围。
 */
@Service
public class EnterprisePurchaseOrderService {

    @Resource
    private ProductPurchaseMemberApi purchaseMemberApi;
    @Resource
    private TradeOrderMapper orderMapper;
    @Resource
    private TradeOrderUpdateService orderUpdateService;
    @Resource
    private TradeOrderLogisticsService logisticsService;

    public PageResult<TradeOrderDO> getPage(Long memberId, AppTradeOrderPageReqVO reqVO) {
        return orderMapper.selectEnterpriseInquiryPage(reqVO, requireScope(memberId).enterpriseId());
    }

    public TradeOrderDO getOrder(Long memberId, Long orderId) {
        return requireOrder(requireScope(memberId), orderId);
    }

    /** 返回服务端核验后的当前采购企业编号，供企业采购子域复用同一套成员与租户边界。 */
    public Long requireEnterpriseId(Long memberId) {
        return requireScope(memberId).enterpriseId();
    }

    public Map<String, Long> getCount(Long memberId) {
        Long enterpriseId = requireScope(memberId).enterpriseId();
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("allCount", orderMapper.selectEnterpriseInquiryCount(enterpriseId, null, null));
        result.put("unpaidCount", orderMapper.selectEnterpriseInquiryCount(enterpriseId,
                TradeOrderStatusEnum.UNPAID.getStatus(), null));
        result.put("undeliveredCount", orderMapper.selectEnterpriseInquiryCount(enterpriseId,
                TradeOrderStatusEnum.UNDELIVERED.getStatus(), null));
        result.put("deliveredCount", orderMapper.selectEnterpriseInquiryCount(enterpriseId,
                TradeOrderStatusEnum.DELIVERED.getStatus(), null));
        result.put("uncommentedCount", orderMapper.selectEnterpriseInquiryCount(enterpriseId,
                TradeOrderStatusEnum.COMPLETED.getStatus(), false));
        result.put("afterSaleCount", 0L); // 售后仍严格属于实际下单会员，不在企业接口中放宽。
        return result;
    }

    public void receive(Long memberId, Long orderId) {
        TradeOrderDO order = getOrder(memberId, orderId);
        orderUpdateService.receiveOrderByMember(order.getUserId(), orderId);
    }

    public void cancel(Long memberId, Long orderId) {
        TradeOrderDO order = getOrder(memberId, orderId);
        orderUpdateService.cancelOrderByMember(order.getUserId(), orderId);
    }

    public void updateAddress(Long memberId, AppTradeOrderUpdateAddressReqVO reqVO) {
        ProductPurchaseMemberApi.Scope scope = requireScope(memberId);
        requireOrder(scope, reqVO.getId());
        orderUpdateService.updateInquiryOrderAddressByEnterprise(memberId, scope.enterpriseId(), reqVO);
    }

    public AppTradeOrderLogisticsRespVO getLogistics(Long memberId, Long orderId) {
        return logisticsService.getForAuthorizedOrder(getOrder(memberId, orderId));
    }

    private ProductPurchaseMemberApi.Scope requireScope(Long memberId) {
        ProductPurchaseMemberApi.Scope scope = purchaseMemberApi.getScope(memberId).getCheckedData();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (scope == null || scope.memberId() == null || scope.enterpriseId() == null
                || scope.enterpriseId() <= 0 || !Objects.equals(scope.memberId(), memberId)
                || !Objects.equals(scope.tenantId(), tenantId)) {
            throw exception(ORDER_NOT_FOUND);
        }
        return scope;
    }

    private TradeOrderDO requireOrder(ProductPurchaseMemberApi.Scope scope, Long orderId) {
        TradeOrderDO order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null || !TradeOrderDO.SOURCE_TYPE_INQUIRY.equals(order.getSourceType())
                || !Objects.equals(order.getBuyerEnterpriseId(), scope.enterpriseId())) {
            // 对不存在、普通订单、跨企业订单统一返回未找到，避免枚举订单归属。
            throw exception(ORDER_NOT_FOUND);
        }
        return order;
    }
}
