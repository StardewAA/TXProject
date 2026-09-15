package cn.iocoder.txgy.module.wms.service.integration;

import cn.iocoder.txgy.framework.common.util.json.JsonUtils;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** Immutable per-line event versions double as a durable outbox. Every SQL business access is tenant-scoped. */
@Service
public class WmsShortageService {
    @Resource private JdbcTemplate jdbc;
    @Resource private Environment environment;
    static final String LATEST = " AND NOT EXISTS (SELECT 1 FROM wms_shortage_event newer"
            + " WHERE newer.tenant_id=e.tenant_id AND newer.event_id=e.event_id AND newer.event_version>e.event_version)";

    /** This specific exception preserves the checked shortage transaction while preventing BPM submission. */
    public static class ShortageBlockedException extends RuntimeException {
        public ShortageBlockedException() { super("库存不足，缺口已记录；请补货后重新检查库存，再提交仓库审批"); }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean check(Long orderId) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var order = order(orderId, tenant, true);
        String source = Objects.toString(order.get("source_module"), "");
        int type = ((Number) order.get("type")).intValue();
        boolean mes = source.equals("MES") && type == 202;
        if (!mes && !(source.equals("ERP") && type == 201)) return false;
        if (((Number) order.get("status")).intValue() == 5) {
            cancel(orderId); return false;
        }
        if (((Number) order.get("execution_status")).intValue() == 1) return false;
        var lines = jdbc.queryForList("SELECT * FROM wms_shipment_order_detail WHERE tenant_id=? AND order_id=?"
                + " AND deleted=0 ORDER BY source_line_no,id", tenant, orderId);
        var remaining = new HashMap<Long, BigDecimal>();
        boolean shortage = false;
        for (var line : lines) {
            long sku = ((Number) line.get("sku_id")).longValue();
            long warehouse = ((Number) order.get("warehouse_id")).longValue();
            var balances = jdbc.queryForList("SELECT quantity FROM wms_inventory WHERE tenant_id=? AND sku_id=?"
                    + " AND warehouse_id=? AND deleted=0 FOR UPDATE", tenant, sku, warehouse);
            BigDecimal current = balances.isEmpty() ? BigDecimal.ZERO : decimal(balances.getFirst().get("quantity"));
            BigDecimal requested = decimal(line.get("quantity"));
            BigDecimal left = remaining.getOrDefault(sku, current.max(BigDecimal.ZERO));
            BigDecimal gap = requested.subtract(left).max(BigDecimal.ZERO);
            remaining.put(sku, left.subtract(requested).max(BigDecimal.ZERO));
            shortage |= gap.signum() > 0;
            var previous = jdbc.queryForList("SELECT * FROM wms_shortage_event WHERE tenant_id=? AND order_id=?"
                    + " AND line_id=? ORDER BY event_version DESC LIMIT 1 FOR UPDATE", tenant, orderId, line.get("id"));
            if (gap.signum() == 0 && previous.isEmpty()) continue;
            Map<String, Object> payload = payload(order, line, tenant, mes, current, requested, gap);
            append(order, line, payload, previous.isEmpty() ? null : previous.getFirst());
        }
        return shortage;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Map<String, Object>> recheck(Long orderId) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var order = order(orderId, tenant, true);
        if (((Number) order.get("approval_status")).intValue() == 1)
            throw new IllegalArgumentException("审批中不能重新检查并重置单据");
        boolean shortage = check(orderId);
        if (!shortage && ((Number) order.get("status")).intValue() == 0
                && ((Number) order.get("execution_status")).intValue() == 2) {
            jdbc.update("UPDATE wms_shipment_order SET approval_status=-1, execution_status=0, execution_error=NULL"
                    + " WHERE tenant_id=? AND id=? AND deleted=0", tenant, orderId);
        }
        return list(orderId);
    }

    public List<Map<String, Object>> list(Long orderId) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var order = order(orderId, tenant, false);
        boolean simulation = Boolean.TRUE.equals(order.get("simulation")) || Objects.equals(order.get("simulation"), 1);
        return jdbc.queryForList("SELECT e.* FROM wms_shortage_event e WHERE e.tenant_id=? AND e.order_id=?"
                        + LATEST + " ORDER BY e.line_id", tenant, orderId).stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(JsonUtils.parseObject((String) row.get("payload"), Map.class));
            result.put("notificationStatus", simulation ? "SIMULATED" : row.get("notification_status"));
            result.put("attempts", row.get("attempts"));
            result.put("lastError", row.get("last_error"));
            result.put("lastSentTime", row.get("last_sent_time"));
            result.put("succeededTime", row.get("succeeded_time"));
            return result;
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long orderId) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        var order = order(orderId, tenant, true);
        var seen = new HashSet<String>();
        for (var row : jdbc.queryForList("SELECT * FROM wms_shortage_event WHERE tenant_id=? AND order_id=?"
                + " ORDER BY event_version DESC FOR UPDATE", tenant, orderId)) {
            if (!seen.add((String)row.get("event_id"))) continue;
            Map<String,Object> payload = new LinkedHashMap<>(JsonUtils.parseObject((String) row.get("payload"), Map.class));
            if ("CANCELLED".equals(payload.get("shortageStatus"))) continue;
            payload.put("shortageStatus", "CANCELLED");
            append(order, Map.of("id",row.get("line_id")), payload, row);
        }
    }

    private void append(Map<String,Object> order, Map<String,Object> line, Map<String,Object> payload,
                        Map<String,Object> previous) {
        if (previous != null) {
            var old = JsonUtils.parseObject((String) previous.get("payload"), Map.class);
            if (Objects.equals(old.get("shortageStatus"),payload.get("shortageStatus"))
                    && decimal(old.get("currentQuantity")).compareTo(decimal(payload.get("currentQuantity"))) == 0
                    && decimal(old.get("requestedQuantity")).compareTo(decimal(payload.get("requestedQuantity"))) == 0
                    && decimal(old.get("shortageQuantity")).compareTo(decimal(payload.get("shortageQuantity"))) == 0) return;
        }
        String eventId = previous == null ? UUID.randomUUID().toString() : (String)previous.get("event_id");
        int version = previous == null ? 1 : ((Number)previous.get("event_version")).intValue()+1;
        payload.put("eventId", eventId);
        payload.put("eventVersion",version);
        payload.put("occurredAt",LocalDateTime.now().toString());
        String target = (String) payload.get("targetSystem");
        long tenant = TenantContextHolder.getRequiredTenantId();
        boolean simulation = Boolean.TRUE.equals(order.get("simulation")) || Objects.equals(order.get("simulation"), 1);
        String state = simulation ? "SIMULATED" : configured(tenant,target) ? "PENDING" : "WAITING_INTEGRATION";
        jdbc.update("INSERT INTO wms_shortage_event(tenant_id,event_id,event_version,order_id,line_id,target_system,"
                        + "shortage_status,payload,notification_status,next_attempt_time) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                tenant,eventId,version,order.get("id"),line.get("id"),target,payload.get("shortageStatus"),
                JsonUtils.toJsonString(payload),state);
    }

    boolean configured(long tenant, String target) {
        return !setting(tenant,target,"url").isBlank() && !setting(tenant,target,"token").isBlank();
    }
    String setting(long tenant, String target, String key) {
        return environment.getProperty("wms.shortage.targets."+target.toLowerCase(Locale.ROOT)+"."+tenant+"."+key,"");
    }

    private Map<String,Object> payload(Map<String,Object> order, Map<String,Object> line, long tenant, boolean mes,
                                       BigDecimal current, BigDecimal requested, BigDecimal gap) {
        Map<String,Object> snapshot = line.get("source_snapshot_json") == null ? Map.of()
                : JsonUtils.parseObject((String)line.get("source_snapshot_json"), Map.class);
        var warehouses = jdbc.queryForList("SELECT code,name FROM wms_warehouse WHERE tenant_id=? AND id=? AND deleted=0",
                tenant,order.get("warehouse_id"));
        var warehouse = warehouses.isEmpty() ? Map.<String,Object>of() : warehouses.getFirst();
        var p = new LinkedHashMap<String,Object>();
        p.put("tenantId",tenant); p.put("senderSystem","WMS");
        p.put("sourceSystem",mes ? "MES" : "ERP"); p.put("targetSystem",mes ? "ERP" : "MES");
        p.put("businessType",mes ? "PRODUCTION_SHIPMENT" : "SALE_SHIPMENT");
        p.put("eventType",mes ? "PRODUCTION_RAW_MATERIAL_SHORTAGE" : "SALES_FINISHED_GOODS_SHORTAGE");
        p.put("sourceOrderNo",order.get("biz_order_no"));
        p.put("sourceLineNo",line.get("source_line_no") == null ? line.get("id") : line.get("source_line_no"));
        p.put("wmsOrderNo",order.get("no")); p.put("warehouseId",order.get("warehouse_id"));
        p.put("warehouseCode",Objects.toString(warehouse.get("code"),Objects.toString(order.get("warehouse_id"))));
        p.put("warehouseName",Objects.toString(warehouse.get("name"),""));
        p.put("skuId",line.get("sku_id")); p.put("skuCode",Objects.toString(snapshot.get("skuCode"),Objects.toString(line.get("sku_id"))));
        p.put("skuName",Objects.toString(snapshot.get("materialNameSnapshot"),Objects.toString(p.get("skuCode"))));
        p.put("unit",Objects.toString(snapshot.get("unitNameSnapshot"),""));
        p.put("requestedQuantity",requested); p.put("currentQuantity",current);
        p.put("reservedQuantity",BigDecimal.ZERO); p.put("reservationSupported",false);
        p.put("availableQuantity",current); p.put("shortageQuantity",gap);
        p.put("shortageStatus",gap.signum()>0 ? "OPEN" : "RESOLVED");
        return p;
    }

    private Map<String,Object> order(Long id, long tenant, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM wms_shipment_order WHERE tenant_id=? AND id=? AND deleted=0"
                + (lock ? " FOR UPDATE" : ""), tenant,id);
        if (rows.isEmpty()) throw new IllegalArgumentException("出库单不存在或不属于当前租户");
        return rows.getFirst();
    }
    static BigDecimal decimal(Object value) { return new BigDecimal(String.valueOf(value)); }
}
