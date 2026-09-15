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
import java.time.LocalDateTime;
import java.util.*;

@Service
public class WmsShortageSender {
    @Resource private JdbcTemplate jdbc;
    @Resource private WmsShortageService shortages;
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
        if (!shortages.configured(tenant,target)) {
            jdbc.update("UPDATE wms_shortage_event SET notification_status='WAITING_INTEGRATION',last_error='接收地址或访问令牌未配置',next_attempt_time=?"
                    + " WHERE tenant_id=? AND event_id=? AND event_version=?",LocalDateTime.now().plusMinutes(5),tenant,eventId,version);
            return false;
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
        jdbc.update("UPDATE wms_shortage_event SET notification_status=?,last_error=?,next_attempt_time=?,succeeded_time=?"
                        + " WHERE tenant_id=? AND event_id=? AND event_version=?",
                success ? "SUCCEEDED":"FAILED",error,next,success ? now:null,tenant,eventId,version);
        return success;
    }
}
