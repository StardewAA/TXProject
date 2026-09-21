package cn.iocoder.txgy.module.wms.service.integration;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WMS 协作单 G 状态机 Service（设计 6.3 / 9.1）
 *
 * <p>成品库存不足时，按出库单行项生成协作单 G 草稿（DRAFT）；WMS 人员审批通过后（APPROVED）
 * 才允许调度器把缺口事件发送给 MES；MES 确认后回填生产工单号（CONFIRMED），生产完工闭环后 COMPLETED。</p>
 *
 * <p>审批采用单据状态机而非 BPM：{@link #approve} / {@link #reject} 直接驱动状态流转，预留 BPM 接入点。</p>
 */
@Service
@Slf4j
public class WmsCollaborationOrderGService {

    @Resource
    private JdbcTemplate jdbc;

    /** 缺口生成或刷新协作单 G 草稿；缺口消除则置 CANCELLED。键：(tenant, wms_order_id, wms_order_line_id)。 */
    @Transactional(rollbackFor = Exception.class)
    public void upsertDraft(long tenant, Map<String, Object> order, Map<String, Object> line, Map<String, Object> payload) {
        BigDecimal shortage = decimal(payload.get("shortageQuantity"));
        var existing = jdbc.queryForList(
                "SELECT id,status FROM wms_collaboration_order_g WHERE tenant_id=? AND wms_order_id=? AND wms_order_line_id=? AND deleted=0",
                tenant, order.get("id"), line.get("id"));
        if (existing.isEmpty()) {
            if (shortage.signum() <= 0) {
                return; // 无缺口，不生成协作单
            }
            String gNo = "CG" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            jdbc.update("INSERT INTO wms_collaboration_order_g(tenant_id,g_no,source_order_no,wms_order_id,wms_order_no,"
                            + "wms_order_line_id,sku_id,sku_code,sku_name,warehouse_id,warehouse_code,requested_quantity,"
                            + "available_quantity,shortage_quantity,reserved_quantity,required_date,status,creator) "
                            + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    tenant, gNo, order.get("biz_order_no"), order.get("id"), order.get("no"), line.get("id"),
                    line.get("sku_id"), payload.get("skuCode"), payload.get("skuName"), order.get("warehouse_id"),
                    payload.get("warehouseCode"), decimal(payload.get("requestedQuantity")), decimal(payload.get("availableQuantity")),
                    shortage, decimal(payload.get("reservedQuantity")), null, "DRAFT", "WMS-SHORTAGE");
            return;
        }
        var row = existing.getFirst();
        String status = (String) row.get("status");
        if ("CONFIRMED".equals(status) || "COMPLETED".equals(status)) {
            return; // 终态，不回退
        }
        if (shortage.signum() <= 0) {
            jdbc.update("UPDATE wms_collaboration_order_g SET status='CANCELLED',update_time=NOW() "
                    + "WHERE tenant_id=? AND id=?", tenant, row.get("id"));
        } else {
            jdbc.update("UPDATE wms_collaboration_order_g SET shortage_quantity=?,available_quantity=?,"
                            + "requested_quantity=?,update_time=NOW() WHERE tenant_id=? AND id=? AND status IN ('DRAFT','APPROVED','REJECTED','SENT')",
                    shortage, decimal(payload.get("availableQuantity")), decimal(payload.get("requestedQuantity")), tenant, row.get("id"));
        }
    }

    /** 当前协作单 G 状态；不存在返回 null。 */
    public String statusOf(long tenant, long orderId, long lineId) {
        var rows = jdbc.queryForList(
                "SELECT status FROM wms_collaboration_order_g WHERE tenant_id=? AND wms_order_id=? AND wms_order_line_id=? AND deleted=0",
                tenant, orderId, lineId);
        return rows.isEmpty() ? null : (String) rows.getFirst().get("status");
    }

    /** 审批通过：DRAFT/REJECTED → APPROVED，并唤醒等待中的缺口事件以便调度器发送。 */
    @Transactional(rollbackFor = Exception.class)
    public void approve(long tenant, String gNo, long userId) {
        var rows = jdbc.queryForList(
                "SELECT wms_order_id,wms_order_line_id FROM wms_collaboration_order_g "
                        + "WHERE tenant_id=? AND g_no=? AND deleted=0 AND status IN ('DRAFT','REJECTED')",
                tenant, gNo);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("协作单 G 不存在或状态不可审批");
        }
        var r = rows.getFirst();
        jdbc.update("UPDATE wms_collaboration_order_g SET status='APPROVED',updater=?,update_time=NOW() "
                + "WHERE tenant_id=? AND g_no=?", String.valueOf(userId), tenant, gNo);
        // 唤醒因未审批而挂起的缺口事件，交还调度器发送
        jdbc.update("UPDATE wms_shortage_event SET notification_status='PENDING',attempts=0,next_attempt_time=CURRENT_TIMESTAMP "
                + "WHERE tenant_id=? AND order_id=? AND line_id=? AND target_system='MES' AND notification_status='WAITING_APPROVAL'",
                tenant, r.get("wms_order_id"), r.get("wms_order_line_id"));
    }

    /** 驳回：DRAFT/APPROVED → REJECTED，必须填原因（设计 9.1）。 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(long tenant, String gNo, long userId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("驳回必须填写原因");
        }
        int n = jdbc.update("UPDATE wms_collaboration_order_g SET status='REJECTED',reject_reason=?,updater=?,update_time=NOW() "
                + "WHERE tenant_id=? AND g_no=? AND deleted=0 AND status IN ('DRAFT','APPROVED')", reason, String.valueOf(userId), tenant, gNo);
        if (n == 0) {
            throw new IllegalArgumentException("协作单 G 不存在或状态不可驳回");
        }
    }

    /** 发送成功后由调度器回填 SENT。 */
    @Transactional(rollbackFor = Exception.class)
    public void markSent(long tenant, long orderId, long lineId) {
        jdbc.update("UPDATE wms_collaboration_order_g SET status='SENT',update_time=NOW() "
                + "WHERE tenant_id=? AND wms_order_id=? AND wms_order_line_id=? AND deleted=0 AND status='APPROVED'",
                tenant, orderId, lineId);
    }

    /** MES 确认后回填生产工单号（CONFIRMED）。 */
    @Transactional(rollbackFor = Exception.class)
    public void markConfirmed(long tenant, long orderId, long lineId, String mesWorkOrderNo) {
        jdbc.update("UPDATE wms_collaboration_order_g SET status='CONFIRMED',mes_work_order_no=?,update_time=NOW() "
                + "WHERE tenant_id=? AND wms_order_id=? AND wms_order_line_id=? AND deleted=0 AND status IN ('SENT','APPROVED')",
                mesWorkOrderNo, tenant, orderId, lineId);
    }

    /** MES 确认后按来源单号+物料回写生产工单号（供 MES 通过报文回写，无需 WMS 内部 ID）。 */
    @Transactional(rollbackFor = Exception.class)
    public void markConfirmedBySource(long tenant, String sourceOrderNo, String skuCode, String mesWorkOrderNo) {
        int n = jdbc.update("UPDATE wms_collaboration_order_g SET status='CONFIRMED',mes_work_order_no=?,update_time=NOW() "
                + "WHERE tenant_id=? AND source_order_no=? AND sku_code=? AND deleted=0 AND status IN ('SENT','APPROVED')",
                mesWorkOrderNo, tenant, sourceOrderNo, skuCode);
        if (n == 0) {
            log.warn("[协作单G] MES 确认回写未命中：tenant={}, sourceOrderNo={}, skuCode={}", tenant, sourceOrderNo, skuCode);
        }
    }

    /** MES 驳回后按来源单号+物料回写原因。 */
    @Transactional(rollbackFor = Exception.class)
    public void markRejectedBySource(long tenant, String sourceOrderNo, String skuCode, String reason) {
        int n = jdbc.update("UPDATE wms_collaboration_order_g SET status='REJECTED',reject_reason=?,update_time=NOW() "
                + "WHERE tenant_id=? AND source_order_no=? AND sku_code=? AND deleted=0 AND status IN ('SENT','APPROVED','DRAFT')",
                reason, tenant, sourceOrderNo, skuCode);
        if (n == 0) {
            log.warn("[协作单G] MES 驳回回写未命中：tenant={}, sourceOrderNo={}, skuCode={}", tenant, sourceOrderNo, skuCode);
        }
    }

    public List<Map<String, Object>> listByOrder(long tenant, long orderId) {
        return jdbc.queryForList(
                "SELECT * FROM wms_collaboration_order_g WHERE tenant_id=? AND wms_order_id=? AND deleted=0 ORDER BY id",
                tenant, orderId);
    }

    public Map<String, Object> page(long tenant, int pageNo, int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("invalid page");
        }
        var list = jdbc.queryForList(
                "SELECT * FROM wms_collaboration_order_g WHERE tenant_id=? AND deleted=0 ORDER BY id DESC LIMIT ? OFFSET ?",
                tenant, pageSize, (pageNo - 1) * pageSize);
        long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM wms_collaboration_order_g WHERE tenant_id=? AND deleted=0", Long.class, tenant);
        return Map.of("list", list, "total", total);
    }

    static BigDecimal decimal(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
