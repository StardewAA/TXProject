package cn.iocoder.txgy.module.mes.service.shortage;

import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesIntegrationAcceptedRespDTO;
import cn.iocoder.txgy.module.mes.api.integration.dto.MesWorkOrderCreateReqDTO;
import cn.iocoder.txgy.module.mes.service.integration.MesWorkOrderIntegrationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * WMS 缺货协作单 G 自动建 MES 生产工单。
 *
 * <p>WMS 在销售出库缺货时推送协作单 G（eventType=SALES_FINISHED_GOODS_SHORTAGE，skuCode=缺货成品，
 * shortageQuantity=缺口量）到 MES。本服务在收件箱落库后，按幂等键（缺货事件 eventId）自动创建一个
 * 「自行生产」的草稿工单，缺口量即生产量。收件箱本身保持无工作流依赖，本服务由 Controller 在接收后调用。</p>
 *
 * <p>异常外抛：建单失败时不吞异常，交由 Controller 返回错误，使 WMS 按自身重试策略重发；
 * 收件箱落库与建单两侧均幂等，重发不会产生重复工单。</p>
 *
 * @author OPENLAB BS
 */
@Service
@Slf4j
public class MesWmsShortageWorkOrderAutoService {

    @Resource
    private MesWorkOrderIntegrationService mesWorkOrderIntegrationService;

    /**
     * MES 确认协作单 G 后创建生产工单（设计 6.3.6）。异常外抛，交由调用方决定是否让 WMS 重试。
     *
     * @param payload  已通过收件箱校验的协作单 G 报文
     * @param tenantId 租户
     * @return 创建/已存在的生产工单号
     */
    public String tryCreateWorkOrder(Map<String, Object> payload, long tenantId) {
        String shortageStatus = (String) payload.get("shortageStatus");
        // RESOLVED / CANCELLED 不建单（缺口已消除或已取消）
        if (!"OPEN".equals(shortageStatus)) {
            return null;
        }
        String eventId = (String) payload.get("eventId");
        String skuCode = (String) payload.get("skuCode");
        String skuName = (String) payload.get("skuName");
        BigDecimal shortageQuantity = (BigDecimal) payload.get("shortageQuantity");
        Long eventVersion = ((Number) payload.get("eventVersion")).longValue();
        String occurredAt = (String) payload.get("occurredAt");
        String sourceOrderNo = (String) payload.get("sourceOrderNo");

        MesWorkOrderCreateReqDTO req = new MesWorkOrderCreateReqDTO();
        req.setOrderSourceCode(eventId);
        req.setItemCode(skuCode);
        req.setProductName(skuName);
        req.setQuantity(shortageQuantity);
        req.setRequestDate(LocalDateTime.parse(occurredAt));
        req.setRemark("由 WMS 缺货协作单 G（" + eventId + "，来源单 " + sourceOrderNo + "）自动下发");
        req.setRequestId(eventId);
        req.setDataVersion(eventVersion);
        req.setTenantId(tenantId);
        req.setSourceModule("WMS");
        req.setOccurredAt(LocalDateTime.parse(occurredAt));
        // WMS 为 Http 直推，调用线程无租户上下文；建工单全程依赖 TenantContextHolder 注入 tenant_id，
        // 故在此用 TenantUtils 显式包裹，确保 MES 侧主数据查询与工单落库能正确定位租户。
        final String[] workOrderNo = {null};
        TenantUtils.execute(tenantId, () -> {
            MesIntegrationAcceptedRespDTO resp = mesWorkOrderIntegrationService.createWorkOrderFromShortage(req);
            workOrderNo[0] = resp.getTargetCode();
        });
        log.info("[tryCreateWorkOrder][协作单 G({}) 已触发建生产工单({})，物料({}) 数量({})]",
                eventId, workOrderNo[0], skuCode, shortageQuantity);
        return workOrderNo[0];
    }

}
