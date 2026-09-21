package cn.iocoder.txgy.module.mes.service.wms.integration;

import cn.iocoder.txgy.module.mes.dal.dataobject.wms.integration.MesWmsIntegrationTaskDO;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderWmsStatusEnum;
import cn.iocoder.txgy.module.mes.enums.wms.MesWmsIntegrationOrderTypeEnum;
import cn.iocoder.txgy.module.mes.service.pro.workorder.MesProWorkOrderService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * MES↔WMS 集成结果处理器：生产完工入库（PRODUCTION_RECEIPT）
 *
 * <p>WMS 对成品入库单的处理到达终态时，回写 MES 生产工单的入库结果，
 * 由 {@link MesWmsIntegrationServiceImpl} 中的终态分发逻辑（按 orderType）调用。</p>
 *
 * @author OPENLAB BS
 */
@Slf4j
@Component
public class MesWmsProductionReceiptResultHandler implements MesWmsIntegrationResultHandler {

    @Resource
    @Lazy
    private MesProWorkOrderService workOrderService;

    @Override
    public MesWmsIntegrationOrderTypeEnum supportedOrderType() {
        return MesWmsIntegrationOrderTypeEnum.PRODUCTION_RECEIPT;
    }

    @Override
    public void onTerminal(MesWmsIntegrationTaskDO task, String wmsStatus, Map<String, Object> resultData) {
        Long workOrderId = task.getSourceOrderId();
        MesProWorkOrderWmsStatusEnum status = mapWmsStatus(wmsStatus);
        String orderNo = task.getWmsOrderNo();
        log.info("[入库结果回写][工单({}) WMS单号({}) 终态({}) -> MES状态({})]",
                workOrderId, orderNo, wmsStatus, status.getDesc());
        workOrderService.updateWmsReceiptResult(workOrderId, orderNo, status.getStatus());
        if (resultData != null && resultData.get("completedQuantity") != null) {
            log.info("[入库结果回写][工单({}) 入库完成数量({})]", workOrderId, resultData.get("completedQuantity"));
        }
    }

    private MesProWorkOrderWmsStatusEnum mapWmsStatus(String wmsStatus) {
        if (wmsStatus == null) {
            return MesProWorkOrderWmsStatusEnum.FAILED;
        }
        return switch (wmsStatus) {
            case "SUCCESS" -> MesProWorkOrderWmsStatusEnum.DONE;
            case "EXECUTION_FAILED" -> MesProWorkOrderWmsStatusEnum.FAILED;
            case "REJECTED", "CANCELLED" -> MesProWorkOrderWmsStatusEnum.CANCELLED;
            default -> MesProWorkOrderWmsStatusEnum.FAILED;
        };
    }

}
