package cn.iocoder.txgy.module.trade.service.order;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.member.api.platform.MemberPlatformAccountApi;
import cn.iocoder.txgy.module.member.api.platform.dto.MemberPlatformAccountRespDTO;
import cn.iocoder.txgy.module.trade.controller.app.order.vo.AppTradeOrderPageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.txgy.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/** Read boundary for a buyer's local orders and platform-checkout child orders. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformBuyerOrderQueryService {

    private final TradeOrderMapper orders;
    private final TradeOrderItemMapper items;
    private final MemberPlatformAccountApi platformAccounts;

    public PageResult<TradeOrderDO> page(Long legacyMemberId, Long sourceTenantId,
                                         AppTradeOrderPageReqVO request) {
        Long platformAccountId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<TradeOrderDO> rows = orders.selectAppBuyerPage(legacyMemberId, sourceTenantId, platformAccountId,
                request.getStatus(), request.getCommentStatus(), offset, request.getPageSize());
        Long total = orders.selectAppBuyerCount(legacyMemberId, sourceTenantId, platformAccountId,
                request.getStatus(), request.getCommentStatus());
        return new PageResult<>(rows, total == null ? 0L : total);
    }

    public TradeOrderDO get(Long orderId, Long legacyMemberId, Long sourceTenantId) {
        return orders.selectAppBuyerOwnedOrder(orderId, legacyMemberId, sourceTenantId,
                resolvePlatformAccountId(legacyMemberId, sourceTenantId));
    }

    public AuthorizedOrder getAuthorized(Long orderId, Long legacyMemberId, Long sourceTenantId) {
        Long platformAccountId = resolvePlatformAccountId(legacyMemberId, sourceTenantId);
        TradeOrderDO order = orders.selectAppBuyerOwnedOrder(
                orderId, legacyMemberId, sourceTenantId, platformAccountId);
        if (order == null) return null;
        Long tenantId = orders.selectAppBuyerOwnedOrderTenantId(
                orderId, legacyMemberId, sourceTenantId, platformAccountId);
        return tenantId == null ? null : new AuthorizedOrder(order, tenantId);
    }

    public List<TradeOrderItemDO> items(Collection<Long> authorizedOrderIds) {
        if (authorizedOrderIds == null || authorizedOrderIds.isEmpty()) return List.of();
        return items.selectListByAuthorizedOrderIds(authorizedOrderIds);
    }

    public Long itemTenant(Long orderItemId, Long legacyMemberId, Long sourceTenantId) {
        if (orderItemId == null) return null;
        return orders.selectAppBuyerOwnedOrderTenantIdByItem(orderItemId, legacyMemberId, sourceTenantId,
                resolvePlatformAccountId(legacyMemberId, sourceTenantId));
    }

    public Long platformAccountId(Long legacyMemberId, Long sourceTenantId) {
        return resolvePlatformAccountId(legacyMemberId, sourceTenantId);
    }

    private Long resolvePlatformAccountId(Long legacyMemberId, Long sourceTenantId) {
        try {
            var result = platformAccounts.resolve(legacyMemberId, sourceTenantId);
            MemberPlatformAccountRespDTO account = result == null ? null : result.getCheckedData();
            return account == null ? null : account.getPlatformAccountId();
        } catch (RuntimeException unavailable) {
            log.warn("平台账号解析失败，暂仅返回当前租户订单，memberId={}, tenantId={}",
                    legacyMemberId, sourceTenantId, unavailable);
            return null;
        }
    }

    public record AuthorizedOrder(TradeOrderDO order, Long tenantId) {
    }
}
