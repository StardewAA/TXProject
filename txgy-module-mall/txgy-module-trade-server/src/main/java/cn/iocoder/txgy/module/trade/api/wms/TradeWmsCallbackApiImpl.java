package cn.iocoder.txgy.module.trade.api.wms;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackItemDTO;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.trade.dal.dataobject.wms.TradeWmsShipmentDO;
import cn.iocoder.txgy.module.trade.dal.mysql.wms.TradeWmsShipmentMapper;
import cn.iocoder.txgy.module.trade.service.wms.TradeWmsOutboundService;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO;
import cn.iocoder.txgy.module.wms.api.integration.dto.WmsOrderRequestDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController
@Validated
@RequiredArgsConstructor
public class TradeWmsCallbackApiImpl implements TradeWmsCallbackApi {

    private final TradeWmsShipmentMapper shipmentMapper;

    @Override
    public CommonResult<Boolean> callbackExecutionResult(TradeWmsExecutionCallbackReqDTO request) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        if (!Objects.equals(tenantId, request.getTenantId())) {
            throw new IllegalArgumentException("WMS 回调租户与请求上下文不一致");
        }
        TradeWmsShipmentDO record = shipmentMapper.selectBySourceOrderNo(request.getSourceOrderNo());
        if (record == null || !Objects.equals(record.getOrderNo(), request.getBusinessChainNo())) {
            throw new IllegalArgumentException("找不到对应的商城 WMS 出库申请");
        }
        if (!Objects.equals(record.getRequestId(), request.getRequestId())) {
            throw new IllegalArgumentException("WMS 回调 requestId 与商城出库申请不一致");
        }
        if (record.getTargetOrderNo() != null && !Objects.equals(record.getTargetOrderNo(), request.getWmsOrderNo())) {
            throw new IllegalArgumentException("WMS 回调单号与已受理出库单不一致");
        }
        if (request.getEventVersion() < record.getEventVersion()) {
            return success(true);
        }
        if (request.getEventVersion() > record.getEventVersion()) {
            throw new IllegalArgumentException("WMS 回调版本超前");
        }
        validateItems(record, request);
        record.setTargetOrderNo(request.getWmsOrderNo()).setStatus(TradeWmsOutboundService.COMPLETED)
                .setLastError(null).setLastSyncedAt(LocalDateTime.now());
        shipmentMapper.updateById(record);
        return success(true);
    }

    private static void validateItems(TradeWmsShipmentDO record, TradeWmsExecutionCallbackReqDTO callback) {
        WmsOrderRequestDTO submitted = JsonUtils.parseObject(record.getRequestJson(), WmsOrderRequestDTO.class);
        if (submitted == null || submitted.getItems() == null) {
            throw new IllegalArgumentException("商城出库申请快照缺失，不能确认 WMS 回调");
        }
        if (record.getShopId() == null || record.getShopId() <= 0
                || !Objects.equals(record.getShopId(), submitted.getShopId())) {
            throw new IllegalArgumentException("商城出库申请快照与记录的店铺归属不一致，不能确认 WMS 回调");
        }
        Map<Integer, WmsIntegrationDocumentItemDTO> expected = submitted.getItems().stream()
                .collect(Collectors.toMap(TradeWmsCallbackApiImpl::sourceLine, Function.identity()));
        if (expected.size() != callback.getItems().size()) {
            throw new IllegalArgumentException("WMS 回调明细数量与出库申请不一致");
        }
        for (TradeWmsExecutionCallbackItemDTO actual : callback.getItems()) {
            WmsIntegrationDocumentItemDTO item = expected.get(actual.getSourceLineNo());
            if (item == null || !Objects.equals(item.getMaterialCode(), actual.getMaterialCode())
                    || !Objects.equals(item.getSkuCode(), actual.getSkuCode())
                    || item.getRequiredQuantity().compareTo(actual.getActualQuantity()) != 0) {
                throw new IllegalArgumentException("WMS 回调明细与商城出库申请不一致，sourceLineNo="
                        + actual.getSourceLineNo());
            }
        }
    }

    private static Integer sourceLine(WmsIntegrationDocumentItemDTO item) {
        return item.getSourceLineNo() == null ? item.getLineNo() : item.getSourceLineNo();
    }
}
