package cn.iocoder.txgy.module.mes.service.shortage;

import cn.hutool.json.JSONUtil;
import org.springframework.core.env.Environment;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

/** A durable notification inbox only: deliberately has no procurement or workflow dependency. */
@Service("mesWmsShortageInboxService")
public class WmsShortageInboxService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Environment environment;
    public WmsShortageInboxService(JdbcTemplate jdbc, PlatformTransactionManager manager, Environment environment) {
        this.jdbc=jdbc; this.tx=new TransactionTemplate(manager); this.environment=environment;
        this.tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void receive(Map<String,Object> input, Long tenant, Long user, Long authenticatedTenant) {
        if(tenant==null || user==null || !tenant.equals(authenticatedTenant)
            || !user.toString().equals(environment.getProperty("mes.wms-shortage.sender-user-id."+tenant))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"WMS service identity is not configured or does not match");
        }
        var p=validate(input);
        if(!tenant.equals(p.get("tenantId"))) bad("tenantId does not match authenticated tenant");
        String event=(String)p.get("eventId"), payload=JSONUtil.toJsonStr(p);
        long version=(Long)p.get("eventVersion");
        var identity=new TreeMap<String,Object>();
        for(String key:List.of("senderSystem","sourceSystem","targetSystem","eventType","businessType","sourceOrderNo","sourceLineNo","wmsOrderNo","warehouseId","skuId")) identity.put(key,p.get(key));
        String identityKey=hash(JSONUtil.toJsonStr(identity));
        // MySQL RR missing-key locks may deadlock on first delivery. Roll back and retry a fresh transaction.
        for(int attempt=0;attempt<3;attempt++) {
            try {
                tx.executeWithoutResult(status -> persist(tenant,event,version,identityKey,payload,(String)p.get("shortageStatus")));
                return;
            } catch(DuplicateKeyException | TransientDataAccessException ex) { if(attempt==2) throw ex; }
        }
    }
    private void persist(long tenant,String event,long version,String identity,String payload,String status) {
        var rows=jdbc.queryForList("SELECT event_version,identity_key FROM mes_wms_shortage_inbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,event);
        if(rows.isEmpty()) {
            jdbc.update("INSERT INTO mes_wms_shortage_inbox(tenant_id,event_id,event_version,identity_key,payload,shortage_status,confirm_status,updated_at) VALUES(?,?,?,?,?,?,'AWAITING_CONFIRM',CURRENT_TIMESTAMP)",tenant,event,version,identity,payload,status);
        } else {
            var current=rows.getFirst();
            if(!identity.equals(current.get("identity_key"))) conflict("eventId reused for another business identity");
            var prior=jdbc.queryForList("SELECT payload FROM mes_wms_shortage_version WHERE tenant_id=? AND event_id=? AND event_version=?",tenant,event,version);
            if(!prior.isEmpty()) {
                if(!payload.equals(prior.getFirst().get("payload"))) conflict("same eventVersion has different content");
                return;
            }
            if(version>((Number)current.get("event_version")).longValue())
                jdbc.update("UPDATE mes_wms_shortage_inbox SET event_version=?,payload=?,shortage_status=?,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND event_id=?",version,payload,status,tenant,event);
        }
        // Remember stale receipts as well, so later conflicts for that version are rejected.
        jdbc.update("INSERT INTO mes_wms_shortage_version(tenant_id,event_id,event_version,payload) VALUES(?,?,?,?)",tenant,event,version,payload);
    }
    public long unreadCount(long tenant,long user) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM mes_wms_shortage_inbox i LEFT JOIN mes_wms_shortage_read r ON r.tenant_id=i.tenant_id AND r.event_id=i.event_id AND r.user_id=? WHERE i.tenant_id=? AND (r.event_version IS NULL OR r.event_version<i.event_version)",Long.class,user,tenant);
    }
    public Map<String,Object> page(long tenant,long user,int pageNo,int pageSize) {
        if(pageNo<1 || pageNo>1000000 || pageSize<1 || pageSize>100) bad("invalid page");
        var list=jdbc.query("SELECT i.payload,i.event_version,r.event_version AS read_version FROM mes_wms_shortage_inbox i LEFT JOIN mes_wms_shortage_read r ON r.tenant_id=i.tenant_id AND r.event_id=i.event_id AND r.user_id=? WHERE i.tenant_id=? ORDER BY i.updated_at DESC,i.event_id LIMIT ? OFFSET ?",
            (rs,row)-> {
                Map<String,Object> item=new HashMap<>(JSONUtil.parseObj(rs.getString("payload")));
                Number read=(Number)rs.getObject("read_version");
                item.put("unread",read==null || read.longValue()<rs.getLong("event_version"));
                return item;
            },user,tenant,pageSize,(pageNo-1)*pageSize);
        return Map.of("list",list,"total",jdbc.queryForObject("SELECT COUNT(*) FROM mes_wms_shortage_inbox WHERE tenant_id=?",Long.class,tenant));
    }
    public void markRead(long tenant,long user,String event,long version) {
        tx.executeWithoutResult(status -> {
            var rows=jdbc.queryForList("SELECT event_version FROM mes_wms_shortage_inbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,event);
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"message not found");
            if(version<1 || version>((Number)rows.getFirst().get("event_version")).longValue()) bad("invalid read version");
            int count=jdbc.update("UPDATE mes_wms_shortage_read SET event_version=CASE WHEN event_version<? THEN ? ELSE event_version END WHERE tenant_id=? AND event_id=? AND user_id=?",version,version,tenant,event,user);
            if(count==0) jdbc.update("INSERT INTO mes_wms_shortage_read(tenant_id,event_id,user_id,event_version) VALUES(?,?,?,?)",tenant,event,user,version);
        });
    }
    /** 取收件箱原始报文（确认时用于建生产工单） */
    public Map<String,Object> getPayload(long tenant, String event) {
        var rows=jdbc.queryForList("SELECT payload FROM mes_wms_shortage_inbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,event);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"message not found");
        return JSONUtil.parseObj((String)rows.getFirst().get("payload"));
    }
    /** MES 确认协作单 G：记录生产工单号（设计 6.3.5/6.3.6） */
    public void confirm(long tenant, String event, long version, String workOrderNo) {
        int n=jdbc.update("UPDATE mes_wms_shortage_inbox SET confirm_status='CONFIRMED',mes_work_order_no=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE tenant_id=? AND event_id=? AND event_version=?",workOrderNo,tenant,event,version);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"message not found or version mismatch");
    }
    /** MES 驳回协作单 G：记录原因（设计 6.3.5，驳回必须填原因） */
    public void reject(long tenant, String event, long version, String reason) {
        int n=jdbc.update("UPDATE mes_wms_shortage_inbox SET confirm_status='REJECTED',reject_reason=?,updated_at=CURRENT_TIMESTAMP "
                + "WHERE tenant_id=? AND event_id=? AND event_version=?",reason,tenant,event,version);
        if(n==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"message not found or version mismatch");
    }
    private static Map<String,Object> validate(Map<String,Object> input) {
        if(input==null) {bad("payload required");}
        var p=new TreeMap<String,Object>();
        for(String key:List.of("eventId","senderSystem","sourceSystem","targetSystem","eventType","businessType","sourceOrderNo","wmsOrderNo","warehouseCode","warehouseName","skuCode","skuName","unit","occurredAt","shortageStatus")) {
            Object value=input.get(key);
            if(!(value instanceof String) || ((String)value).isBlank() || ((String)value).length()>255) bad("invalid "+key);
            p.put(key,value);
        }
        try {if(!UUID.fromString((String)p.get("eventId")).toString().equals(p.get("eventId"))) bad("invalid eventId"); LocalDateTime.parse((String)p.get("occurredAt"));} catch(IllegalArgumentException|java.time.format.DateTimeParseException ex){bad("invalid eventId or occurredAt");}
        for(String key:List.of("tenantId","eventVersion","sourceLineNo","warehouseId","skuId")) {
            try {long value=decimal(input,key).longValueExact(); if(value<0 || (!key.equals("tenantId") && !key.equals("sourceLineNo") && value==0)) bad("invalid "+key); p.put(key,value);}
            catch(ArithmeticException ex){bad("invalid "+key);}
        }
        for(String key:List.of("requestedQuantity","currentQuantity","reservedQuantity","availableQuantity","shortageQuantity")) {
            BigDecimal value=decimal(input,key); if(value.signum()<0 || value.precision()>30 || value.scale()>10) bad("invalid "+key); p.put(key,value.stripTrailingZeros());
        }
        require(p,"senderSystem","WMS"); require(p,"sourceSystem","ERP"); require(p,"targetSystem","MES");
        require(p,"eventType","SALES_FINISHED_GOODS_SHORTAGE"); require(p,"businessType","SALE_SHIPMENT");
        if(!Boolean.FALSE.equals(input.get("reservationSupported"))) bad("reservations unsupported"); p.put("reservationSupported",false);
        var requested=(BigDecimal)p.get("requestedQuantity"); var shortage=(BigDecimal)p.get("shortageQuantity");
        if(requested.signum()<=0 || shortage.compareTo(requested)>0 || ((BigDecimal)p.get("reservedQuantity")).signum()!=0
            || ((BigDecimal)p.get("availableQuantity")).compareTo((BigDecimal)p.get("currentQuantity"))!=0) bad("inconsistent quantities");
        String status=(String)p.get("shortageStatus");
        if(!Set.of("OPEN","RESOLVED","CANCELLED").contains(status) || (status.equals("OPEN") && shortage.signum()<=0) || (status.equals("RESOLVED") && shortage.signum()!=0)) bad("invalid shortage status");
        return p;
    }
    private static BigDecimal decimal(Map<String,Object> p,String key) {
        try {if(!(p.get(key) instanceof Number)) {bad("invalid "+key);} return new BigDecimal(p.get(key).toString());}
        catch(NumberFormatException ex){bad("invalid "+key); return null;}
    }
    private static void require(Map<String,Object> p,String key,String value){if(!value.equals(p.get(key))) bad("invalid "+key);}
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static void bad(String message){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private static void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
