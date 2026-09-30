package cn.iocoder.txgy.module.trade.service.aftersale;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.trade.dal.mysql.aftersale.AfterSaleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.trade.enums.ErrorCodeConstants.AFTER_SALE_NOT_FOUND;

/** Routes a payment refund callback back to the seller tenant that owns the after-sale record. */
@Service
@RequiredArgsConstructor
public class PlatformAfterSaleRefundCallbackService {

    private final AfterSaleMapper afterSales;
    private final AfterSaleService afterSaleService;

    public void updateRefunded(Long afterSaleId, Long orderId, Long payRefundId) {
        Long sellerTenantId = afterSales.selectTenantIdByIdCrossTenant(afterSaleId);
        if (sellerTenantId == null) throw exception(AFTER_SALE_NOT_FOUND);
        TenantUtils.execute(sellerTenantId,
                () -> afterSaleService.updateAfterSaleRefunded(afterSaleId, orderId, payRefundId));
    }
}
