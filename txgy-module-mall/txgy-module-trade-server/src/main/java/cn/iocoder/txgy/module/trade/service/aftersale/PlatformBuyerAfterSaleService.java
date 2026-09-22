package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleCreateReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSaleDeliveryReqVO;
import cn.iocoder.txgy.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.txgy.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import cn.iocoder.txgy.module.trade.service.order.PlatformBuyerOrderQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.AFTER_SALE_NOT_FOUND;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.ORDER_ITEM_NOT_FOUND;

/** Buyer after-sale boundary for local orders and authorized platform-checkout child orders. */
@Service
@RequiredArgsConstructor
public class PlatformBuyerAfterSaleService {

    private final AfterSaleMapper afterSales;
    private final AfterSaleService afterSaleService;
    private final PlatformBuyerOrderQueryService buyerOrders;

    public PageResult<AfterSaleDO> page(Long memberId, Long sourceTenantId, AppAfterSalePageReqVO request) {
        Long platformAccountId = buyerOrders.platformAccountId(memberId, sourceTenantId);
        long offset = (long) (request.getPageNo() - 1) * request.getPageSize();
        List<AfterSaleDO> rows = afterSales.selectAppBuyerPage(memberId, sourceTenantId, platformAccountId,
                request.getStatuses(), offset, request.getPageSize());
        Long total = afterSales.selectAppBuyerCount(memberId, sourceTenantId, platformAccountId,
                request.getStatuses());
        return new PageResult<>(rows, total == null ? 0L : total);
    }

    public AfterSaleDO get(Long memberId, Long sourceTenantId, Long afterSaleId) {
        return afterSales.selectAppBuyerOwnedAfterSale(afterSaleId, memberId, sourceTenantId,
                buyerOrders.platformAccountId(memberId, sourceTenantId));
    }

    public Long create(Long memberId, Long sourceTenantId, AppAfterSaleCreateReqVO request) {
        Long tenantId = buyerOrders.itemTenant(request.getOrderItemId(), memberId, sourceTenantId);
        if (tenantId == null) throw exception(ORDER_ITEM_NOT_FOUND);
        return executeInTenant(tenantId, () -> afterSaleService.createAfterSale(memberId, request));
    }

    public void delivery(Long memberId, Long sourceTenantId, AppAfterSaleDeliveryReqVO request) {
        Long tenantId = afterSaleTenant(request.getId(), memberId, sourceTenantId);
        TenantUtils.execute(tenantId, () -> afterSaleService.deliveryAfterSale(memberId, request));
    }

    public void cancel(Long memberId, Long sourceTenantId, Long afterSaleId) {
        Long tenantId = afterSaleTenant(afterSaleId, memberId, sourceTenantId);
        TenantUtils.execute(tenantId, () -> afterSaleService.cancelAfterSale(memberId, afterSaleId));
    }

    private Long afterSaleTenant(Long afterSaleId, Long memberId, Long sourceTenantId) {
        Long tenantId = afterSales.selectAppBuyerOwnedAfterSaleTenantId(afterSaleId, memberId, sourceTenantId,
                buyerOrders.platformAccountId(memberId, sourceTenantId));
        if (tenantId == null) throw exception(AFTER_SALE_NOT_FOUND);
        return tenantId;
    }

    private static <T> T executeInTenant(Long tenantId, Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        TenantUtils.execute(tenantId, () -> result.set(action.get()));
        return result.get();
    }
}
