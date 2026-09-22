package cn.iocoder.txgy.module.trade.api.order;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.api.order.dto.TradeOrderRespDTO;
import cn.iocoder.txgy.module.trade.api.order.dto.TradeOrderServiceCardRespDTO;
import cn.iocoder.txgy.module.trade.convert.order.TradeOrderConvert;
import cn.iocoder.txgy.module.trade.dal.mysql.order.TradeOrderServiceCardMapper;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.txgy.module.trade.service.order.TradeOrderUpdateService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 订单 API 接口实现类
 *
 * @author HUIHUI
 */
@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
public class TradeOrderApiImpl implements TradeOrderApi {

    @Resource
    private TradeOrderUpdateService tradeOrderUpdateService;
    @Resource
    private TradeOrderQueryService tradeOrderQueryService;
    @Resource
    private TradeOrderServiceCardMapper serviceCardMapper;

    @Override
    public CommonResult<List<TradeOrderRespDTO>> getOrderList(Collection<Long> ids) {
        return success(TradeOrderConvert.INSTANCE.convertList04(tradeOrderQueryService.getOrderList(ids)));
    }

    @Override
    public CommonResult<TradeOrderRespDTO> getOrder(Long id) {
        return success(TradeOrderConvert.INSTANCE.convert(tradeOrderQueryService.getOrder(id)));
    }

    @Override
    @ApiAccessLog(requestEnable = false, responseEnable = false)
    public CommonResult<TradeOrderServiceCardRespDTO> getServiceCard(Long id, Long userId, Long tenantId) {
        if (id == null || id <= 0 || userId == null || userId <= 0 || tenantId == null || tenantId <= 0
                || TenantContextHolder.isIgnore() || !Objects.equals(tenantId, TenantContextHolder.getTenantId())) {
            return success(null);
        }
        var order = serviceCardMapper.selectOwned(id, userId, tenantId);
        if (order == null || !Objects.equals(order.getTenantId(), tenantId)
                || !Objects.equals(order.getUserId(), userId)) return success(null);
        return success(order);
    }

    @Override
    public CommonResult<Boolean> cancelPaidOrder(Long userId, Long orderId, Integer cancelType) {
        tradeOrderUpdateService.cancelPaidOrder(userId, orderId, cancelType);
        return success(true);
    }

}
