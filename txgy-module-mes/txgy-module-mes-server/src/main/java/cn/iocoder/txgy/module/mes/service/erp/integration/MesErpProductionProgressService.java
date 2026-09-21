package cn.iocoder.txgy.module.mes.service.erp.integration;

import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.dal.dataobject.md.item.MesMdItemDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import cn.iocoder.txgy.module.mes.dal.mysql.md.item.MesMdItemMapper;
import cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder.MesProWorkOrderMapper;
import cn.iocoder.txgy.module.mes.enums.pro.MesProWorkOrderSourceTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MES → ERP 生产完工进度回传 Service
 *
 * MES 工单完工时按其来源（ERP 销售订单）回传累计完工合格数量，
 * ERP 侧覆盖式写入销售订单行的已生产数量，重复回传天然幂等。
 *
 * 事务提交后推送，失败仅记日志，不影响 MES 完工主流程。
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class MesErpProductionProgressService {

    private static final String REPORT_PATH = "/rpc-api/erp/production-progress/report";
    private static final String SOURCE_MODULE_MES = "MES";

    @Resource
    private MesProWorkOrderMapper workOrderMapper;
    @Resource
    private MesMdItemMapper itemMapper;
    @Resource
    private ErpServerHttpClient erpServerHttpClient;

    /**
     * 工单完工后回传进度（事务提交后执行）
     *
     * @param workOrderId 工单编号
     */
    public void reportProgressAfterCommit(Long workOrderId) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        MesProWorkOrderDO workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null) {
            log.warn("[reportProgress][工单({}) 不存在，跳过回传]", workOrderId);
            return;
        }
        // 仅回传 ERP 来源工单（MES 自建工单没有 ERP 对端单号）
        if (!MesProWorkOrderSourceTypeEnum.ERP.getType().equals(workOrder.getOrderSourceType())) {
            log.info("[reportProgress][工单({}) 来源非 ERP，无需回传]", workOrder.getCode());
            return;
        }
        if (workOrder.getOrderSourceCode() == null || workOrder.getOrderSourceCode().isBlank()) {
            log.warn("[reportProgress][工单({}) 缺少来源单号，跳过回传]", workOrder.getCode());
            return;
        }
        MesMdItemDO item = itemMapper.selectById(workOrder.getProductId());
        if (item == null) {
            log.warn("[reportProgress][工单({}) 产品物料({}) 不存在，跳过回传]", workOrder.getCode(),
                    workOrder.getProductId());
            return;
        }
        Map<String, Object> request = buildRequest(workOrder, item, tenantId);
        Runnable sender = () -> TenantUtils.execute(tenantId, () -> send(workOrder, request));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sender.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sender.run();
            }
        });
    }

    private Map<String, Object> buildRequest(MesProWorkOrderDO workOrder, MesMdItemDO item, Long tenantId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("requestId", UUID.randomUUID().toString());
        request.put("tenantId", tenantId);
        request.put("sourceModule", SOURCE_MODULE_MES);
        request.put("occurredAt", LocalDateTime.now().toString());
        request.put("saleOrderNo", workOrder.getOrderSourceCode());
        request.put("materialCode", item.getCode());
        request.put("workOrderCode", workOrder.getCode());
        BigDecimal produced = workOrder.getQuantityProduced() == null
                ? BigDecimal.ZERO : workOrder.getQuantityProduced();
        request.put("producedQuantity", produced);
        request.put("finished", true);
        request.put("finishTime", (workOrder.getFinishDate() == null
                ? LocalDateTime.now() : workOrder.getFinishDate()).toString());
        return request;
    }

    private void send(MesProWorkOrderDO workOrder, Map<String, Object> request) {
        try {
            erpServerHttpClient.put(REPORT_PATH, (Long) request.get("tenantId"), request);
            log.info("[reportProgress][工单({}) 回传 ERP 成功，订单({}) 物料({}) 数量({})]",
                    workOrder.getCode(), request.get("saleOrderNo"), request.get("materialCode"),
                    request.get("producedQuantity"));
        } catch (Exception ex) {
            log.error("[reportProgress][工单({}) 回传 ERP 失败，订单({})]",
                    workOrder.getCode(), request.get("saleOrderNo"), ex);
        }
    }

}
