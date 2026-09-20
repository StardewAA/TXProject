package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import cn.iocoder.txgy.module.erp.api.wms.ErpWmsCallbackApi;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpCollaborationBReceiveReqDTO;
import cn.iocoder.txgy.module.erp.api.wms.dto.ErpWmsDocumentItemDTO;

@Service
public class WmsShortageSender {
    @Resource private JdbcTemplate jdbc;
    @Resource private WmsShortageService shortages;
    @Resource private WmsCollaborationOrderGService collaborationOrderGService;
    @Resource private ErpWmsCallbackApi erpWmsCallbackApi;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** A bounded HTTP call under the same order lock as checks serializes versions and workers. */
    @Transactional(rollbackFor = Exception.class)
    public boolean send(String eventId, boolean manual) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var keys = jdbc.queryForList("SELECT order_id FROM wms_shortage_event WHERE tenant_id=? AND event_id=? LIMIT 1",
                tenant,eventId);
        if (keys.isEmpty()) throw new IllegalArgumentException("缺口事件不存在");
        var orders = jdbc.queryForList("SELECT simulation FROM wms_shipment_order WHERE tenant_id=? AND id=? FOR UPDATE",
                tenant,keys.getFirst().get("order_id"));
        // Current read, not a REPEATABLE READ snapshot established before waiting for the order lock.
        var rows = jdbc.queryForList("SELECT * FROM wms_shortage_event WHERE tenant_id=? AND event_id=?"
                + " ORDER BY event_version DESC LIMIT 1 FOR UPDATE",tenant,eventId);
        var row = rows.getFirst();
        int version = ((Number)row.get("event_version")).intValue();
        if ("SUCCEEDED".equals(row.get("notification_status"))) return true;
        if (orders.isEmpty() || Boolean.TRUE.equals(orders.getFirst().get("simulation"))
                || Objects.equals(orders.getFirst().get("simulation"),1)) {
            jdbc.update("UPDATE wms_shortage_event SET notification_status='SIMULATED',next_attempt_time=NULL"
                    + " WHERE tenant_id=? AND event_id=? AND event_version=?",tenant,eventId,version);
            return false;
        }
        String target = (String)row.get("target_system");
        if ("ERP".equals(target)) {
            // 原料缺口事件走专用协作单 B 接口发给 ERP 采购（设计 7.2），不走通用 HTTP 出箱
            return sendCollaborationB(tenant, row, version,
                    ((Number)row.get("attempts")).intValue(), manual);
        }
        if (!shortages.configured(tenant,target)) {
            jdbc.update("UPDATE wms_shortage_event SET notification_status='WAITING_INTEGRATION',last_error='接收地址或访问令牌未配置',next_attempt_time=?"
                    + " WHERE tenant_id=? AND event_id=? AND event_version=?",LocalDateTime.now().plusMinutes(5),tenant,eventId,version);
            return false;
        }
        // 协作单 G 状态机：成品缺货事件（target=MES）须协作单 G 审批通过才发送（设计 6.3.4）
        if ("MES".equals(target)) {
            long gOrder = ((Number) row.get("order_id")).longValue();
            long gLine = ((Number) row.get("line_id")).longValue();
            String gStatus = collaborationOrderGService.statusOf(tenant, gOrder, gLine);
            if (gStatus != null && !"APPROVED".equals(gStatus)) {
                jdbc.update("UPDATE wms_shortage_event SET notification_status='WAITING_APPROVAL',next_attempt_time=?"
                                + " WHERE tenant_id=? AND event_id=? AND event_version=?",
                        LocalDateTime.now().plusMinutes(5), tenant, eventId, version);
                return false;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        int attempts = ((Number)row.get("attempts")).intValue();
        if (!manual && !"WAITING_INTEGRATION".equals(row.get("notification_status"))) {
            java.sql.Timestamp due = (java.sql.Timestamp)row.get("next_attempt_time");
            if (attempts>=5 || due==null || due.toLocalDateTime().isAfter(now)) return false;
        }
        attempts++;
        jdbc.update("UPDATE wms_shortage_event SET notification_status='SENDING',attempts=?,last_sent_time=?"
                + " WHERE tenant_id=? AND event_id=? AND event_version=?",attempts,now,tenant,eventId,version);
        boolean success=false, retryable=true;
        String error="";
        try {
            var request = HttpRequest.newBuilder(URI.create(shortages.setting(tenant,target,"url")))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type","application/json")
                    .header("Authorization","Bearer "+shortages.setting(tenant,target,"token"))
                    .header("tenant-id",Long.toString(tenant))
                    .POST(HttpRequest.BodyPublishers.ofString((String)row.get("payload"))).build();
            var response = client.send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()>=200 && response.statusCode()<300) {
                var body = JsonUtils.parseObject(response.body(),Map.class);
                success = body != null && body.get("code") instanceof Number code && code.intValue()==0
                        && Boolean.TRUE.equals(body.get("data"));
                retryable = false;
                if (!success) error="接收系统未明确确认消息，检查接口契约或业务校验";
            } else {
                retryable=response.statusCode()>=500;
                error="接收系统 HTTP "+response.statusCode()+"，请检查接收服务与鉴权配置";
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); error="发送被中断，等待重试";
        } catch (java.io.IOException network) {
            error="接收系统网络不可达或超时";
        } catch (RuntimeException invalid) {
            retryable=false; error="接收配置或响应格式无效，请检查接口契约";
        }
        LocalDateTime next = !success && retryable && attempts<5 ? now.plusSeconds(60L << (attempts-1)) : null;
        if (success && "MES".equals(target)) {
            collaborationOrderGService.markSent(tenant, ((Number) row.get("order_id")).longValue(),
                    ((Number) row.get("line_id")).longValue());
        }
        jdbc.update("UPDATE wms_shortage_event SET notification_status=?,last_error=?,next_attempt_time=?,succeeded_time=?"
                        + " WHERE tenant_id=? AND event_id=? AND event_version=?",
                success ? "SUCCEEDED":"FAILED",error,next,success ? now:null,tenant,eventId,version);
        return success;
    }

    /**
     * 原料缺口事件（target=ERP）走专用协作单 B 接口发给 ERP 采购（设计 7.2）。
     * 复用 wms_shortage_event 出箱的幂等/重试/状态机；协作单 B 号与 requestId 以 eventId 稳定，
     * ERP 侧按协作单号幂等，缺口数量以首次发送为准。
     */
    private boolean sendCollaborationB(long tenant, Map<String,Object> row, int version, int attempts, boolean manual) {
        LocalDateTime now = LocalDateTime.now();
        int maxAttempts = 5;
        if (!manual && !"WAITING_INTEGRATION".equals(row.get("notification_status"))) {
            java.sql.Timestamp due = (java.sql.Timestamp)row.get("next_attempt_time");
            if (attempts>=maxAttempts || due==null || due.toLocalDateTime().isAfter(now)) return false;
        }
        attempts++;
        String eventId = (String)row.get("event_id");
        jdbc.update("UPDATE wms_shortage_event SET notification_status='SENDING',attempts=?,last_sent_time=?"
                + " WHERE tenant_id=? AND event_id=? AND event_version=?",attempts,now,tenant,eventId,version);
        Map<String,Object> payload = JsonUtils.parseObject((String)row.get("payload"), Map.class);
        var dto = new ErpCollaborationBReceiveReqDTO();
        dto.setRequestId("COB-" + eventId);
        dto.setTenantId(tenant);
        dto.setBusinessChainNo("COB-" + row.get("order_id") + "-" + row.get("line_id"));
        dto.setCollaborationNo("COB-" + eventId);
        dto.setCollaborationType("SHORTAGE_COLLABORATION");
        dto.setSourceModule("WMS");
        dto.setSourceOrderNo(Objects.toString(payload.get("sourceOrderNo"),""));
        dto.setSourceProductionOrderNo(Objects.toString(payload.get("sourceProductionOrderNo"),""));
        dto.setOccurredAt(now);
        dto.setEventVersion((long)version);
        dto.setRequiredAt(now.plusDays(7));
        var item = new ErpWmsDocumentItemDTO();
        item.setLineNo(1);
        Object sl = payload.get("sourceLineNo");
        item.setSourceLineNo(sl instanceof Number n ? n.intValue() : null);
        item.setMaterialCode(Objects.toString(payload.get("materialCode"),""));
        item.setSkuCode(Objects.toString(payload.get("skuCode"),""));
        item.setMaterialType(Objects.toString(payload.get("materialType"),""));
        item.setMaterialNameSnapshot(Objects.toString(payload.get("materialNameSnapshot"), String.valueOf(payload.get("skuName"))));
        item.setUnitCode(Objects.toString(payload.get("unitCode"),""));
        item.setUnitNameSnapshot(Objects.toString(payload.get("unitCode"), String.valueOf(payload.get("unit"))));
        item.setShortageQuantity(new BigDecimal(String.valueOf(payload.get("shortageQuantity"))));
        dto.setItems(List.of(item));
        boolean success=false, retryable=true; String error="";
        try {
            var resp = erpWmsCallbackApi.receiveCollaborationB(dto);
            if (resp != null && Objects.equals(resp.getCode(), 0)) {
                success=true; retryable=false;
            } else {
                error="ERP 接收协作单 B 失败：" + (resp==null?"无响应":resp.getMsg());
                retryable=false;
            }
        } catch (Exception ex) {
            error="ERP 协作单 B 调用异常：" + ex.getMessage();
            retryable=true;
        }
        LocalDateTime next = !success && retryable && attempts<maxAttempts ? now.plusSeconds(60L << (attempts-1)) : null;
        jdbc.update("UPDATE wms_shortage_event SET notification_status=?,last_error=?,next_attempt_time=?,succeeded_time=?"
                + " WHERE tenant_id=? AND event_id=? AND event_version=?",
                success?"SUCCEEDED":"FAILED",error,next,success?now:null,tenant,eventId,version);
        return success;
    }
}
