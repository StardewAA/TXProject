package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.wms.dal.dataobject.order.shipment.WmsShipmentOrderDO;
import cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderMapper;
import cn.iocoder.txgy.module.wms.dal.mysql.order.shipment.WmsShipmentOrderDetailMapper;
import cn.iocoder.txgy.module.trade.api.wms.TradeWmsCallbackApi;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackItemDTO;
import cn.iocoder.txgy.module.trade.api.wms.dto.TradeWmsExecutionCallbackReqDTO;
import cn.iocoder.txgy.module.wms.dal.mysql.integration.WmsIntegrationRequestMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.wms.enums.ErrorCodeConstants.SHIPMENT_ORDER_NOT_EXISTS;

@Service
public class WmsShipmentExecutionService {
    @Resource private WmsShipmentOrderMapper orderMapper;
    @Resource private WmsShipmentOrderDetailMapper detailMapper;
    @Resource private ErpWmsCallbackApi erp;
    @Resource private TradeWmsCallbackApi mall;
    @Resource private WmsIntegrationRequestMapper requestMapper;

    public Map<String, Object> getExecution(Long id) {
        TenantContextHolder.getRequiredTenantId();
        WmsShipmentOrderDO order = orderMapper.selectById(id);
        if (order == null) throw exception(SHIPMENT_ORDER_NOT_EXISTS);
        BigDecimal completed = BigDecimal.ZERO;
        if (Integer.valueOf(1).equals(order.getExecutionStatus())) {
            completed = detailMapper.selectListByOrderId(id).stream()
                    .map(line -> line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targetOrderNo", order.getNo());
        result.put("status", executionStatus(order));
        result.put("completedQuantity", completed);
        result.put("notificationStatus", notificationStatus(order));
        result.put("notificationError", order.getNotificationError());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean retryNotification(Long id) { return notifyOrder(id, true); }

    @Transactional(rollbackFor = Exception.class)
    public boolean notifyIfDue(Long id) { return notifyOrder(id, false); }

    private boolean notifyOrder(Long id, boolean force) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        // The row lock serializes manual retries and scheduler workers. Stock is never touched here.
        WmsShipmentOrderDO order = orderMapper.selectByIdForUpdate(id);
        if (order == null) throw exception(SHIPMENT_ORDER_NOT_EXISTS);
        String state = notificationStatus(order);
        if ("DELIVERED".equals(state)) return true;
        if (!("PENDING".equals(state) || "FAILED".equals(state))) return false;
        LocalDateTime now = LocalDateTime.now();
        if (!force && order.getNextNotificationTime() != null && order.getNextNotificationTime().isAfter(now)) return false;
        order.setNotificationAttempts((order.getNotificationAttempts() == null ? 0 : order.getNotificationAttempts()) + 1);
        boolean delivered = false;
        try {
            if ("MALL".equals(order.getSourceModule())) {
                deliverMall(tenantId, id, order);
            } else {
                deliverErp(tenantId, id, order);
            }
            delivered = true;
            order.setNotificationStatus("DELIVERED").setNotificationError("");
        } catch (Exception failure) {
            String error = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            order.setNotificationStatus("FAILED").setNotificationError(error.substring(0, Math.min(error.length(), 1000)))
                    .setNextNotificationTime(now.plusSeconds(60));
        }
        orderMapper.updateById(new WmsShipmentOrderDO().setId(id).setNotificationStatus(order.getNotificationStatus())
                .setNotificationError(order.getNotificationError()).setNotificationAttempts(order.getNotificationAttempts())
                .setNextNotificationTime(order.getNextNotificationTime()));
        return delivered;
    }

    private void deliverErp(Long tenantId, Long id, WmsShipmentOrderDO order) {
            ErpWmsExecutionCallbackReqDTO request = new ErpWmsExecutionCallbackReqDTO();
            request.setRequestId("WMS-SHIPMENT-" + tenantId + "-" + id + "-1");
            request.setTenantId(tenantId);
            request.setEventVersion(1L);
            request.setOccurredAt(order.getExecutionTime() == null ? order.getOrderTime() : order.getExecutionTime());
            request.setBusinessChainNo(order.getBusinessChainNo());
            request.setSourceOrderNo(order.getBizOrderNo());
            request.setWmsOrderNo(order.getNo());
            request.setOrderType("SALE_SHIPMENT");
            request.setExecutionStatus("COMPLETED");
            request.setItems(detailMapper.selectListByOrderId(id).stream().map(line -> {
                if (line.getSourceSnapshotJson() == null) throw new IllegalStateException("出库明细缺少ERP来源快照");
                ErpWmsDocumentItemDTO item = JsonUtils.parseObject(line.getSourceSnapshotJson(), ErpWmsDocumentItemDTO.class);
                if (item == null) throw new IllegalStateException("ERP来源快照无效");
                item.setSourceLineNo(line.getSourceLineNo());
                item.setActualQuantity(line.getQuantity());
                item.setQualifiedQuantity(line.getQuantity());
                item.setUnqualifiedQuantity(BigDecimal.ZERO);
                return item;
            }).toList());
            var response = erp.callbackExecutionResult(request);
            if (response == null) throw new IllegalStateException("ERP返回为空");
            response.checkError();
            if (!Boolean.TRUE.equals(response.getData())) throw new IllegalStateException("ERP未确认接收执行结果");
    }

    private void deliverMall(Long tenantId, Long id, WmsShipmentOrderDO order) {
        if (order.getShopId() == null || order.getShopId() <= 0) {
            throw new IllegalStateException("历史商城出库单缺少有效店铺归属，须转人工异常处理");
        }
        var sourceRequest = requestMapper.selectSourceRequest("SALE_SHIPMENT_CREATE", order.getNo());
        if (sourceRequest == null) throw new IllegalStateException("商城出库申请号不存在");
        TradeWmsExecutionCallbackReqDTO request = new TradeWmsExecutionCallbackReqDTO()
                .setRequestId(sourceRequest.getRequestId()).setTenantId(tenantId)
                .setEventVersion(sourceRequest.getProcessedEventVersion())
                .setOccurredAt(order.getExecutionTime() == null ? order.getOrderTime() : order.getExecutionTime())
                .setBusinessChainNo(order.getBusinessChainNo()).setSourceOrderNo(order.getBizOrderNo())
                .setWmsOrderNo(order.getNo()).setOrderType("SALE_SHIPMENT").setExecutionStatus("COMPLETED")
                .setItems(detailMapper.selectListByOrderId(id).stream().map(line -> {
                    if (line.getSourceSnapshotJson() == null) throw new IllegalStateException("出库明细缺少商城来源快照");
                    var item = JsonUtils.parseObject(line.getSourceSnapshotJson(),
                            cn.iocoder.txgy.module.wms.api.integration.dto.WmsIntegrationDocumentItemDTO.class);
                    if (item == null) throw new IllegalStateException("商城来源快照无效");
                    return new TradeWmsExecutionCallbackItemDTO().setLineNo(item.getLineNo())
                            .setSourceLineNo(line.getSourceLineNo()).setMaterialCode(item.getMaterialCode())
                            .setSkuCode(item.getSkuCode()).setActualQuantity(line.getQuantity());
                }).toList());
        var response = mall.callbackExecutionResult(request);
        if (response == null) throw new IllegalStateException("商城返回为空");
        response.checkError();
        if (!Boolean.TRUE.equals(response.getData())) throw new IllegalStateException("商城未确认接收执行结果");
    }

    public static String executionStatus(WmsShipmentOrderDO order) {
        if (Integer.valueOf(1).equals(order.getExecutionStatus())) return "SUCCESS";
        if (Integer.valueOf(2).equals(order.getExecutionStatus())) return "EXECUTION_FAILED";
        if (Integer.valueOf(5).equals(order.getStatus()) || Integer.valueOf(4).equals(order.getApprovalStatus())) return "CANCELLED";
        if (Integer.valueOf(3).equals(order.getApprovalStatus())) return "REJECTED";
        if (Integer.valueOf(2).equals(order.getApprovalStatus())) return "WAITING_EXECUTION";
        if (Integer.valueOf(1).equals(order.getApprovalStatus())) return "WAITING_APPROVAL";
        return "DRAFT";
    }

    public static String notificationStatus(WmsShipmentOrderDO order) {
        if (Boolean.TRUE.equals(order.getSimulation())) return "SIMULATED";
        if ("MES".equals(order.getSourceModule())) return "POLLING_ONLY";
        if (!"ERP".equals(order.getSourceModule()) && !"MALL".equals(order.getSourceModule())) return "NOT_REQUIRED";
        if (!Integer.valueOf(1).equals(order.getExecutionStatus())) return "WAITING_EXECUTION";
        return order.getNotificationStatus() == null || order.getNotificationStatus().isBlank() ? "PENDING" : order.getNotificationStatus();
    }
}
