package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 仓储库区创建工具（Function Calling 写操作，三层自动补全）
 *
 * MES 仓储层级为 仓库(mes_wm_warehouse) → 库位(mes_wm_warehouse_location) → 库区(mes_wm_warehouse_area)。
 * 用户往往只给「库区名称/编码/面积/所属仓库名」：本工具自动逐层解析或补建缺失的上层（仓库、库位），
 * 再创建库区。Excel 批量库区导入场景下模型逐行调用本工具即可。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_storage_area_create")
public class MesStorageAreaCreateToolFunction extends MesMdCreateSupport<MesStorageAreaCreateToolFunction.Request> {

    public MesStorageAreaCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 库区创建工具：根据用户提供的仓库/库区信息直接创建真实库区档案（写入数据库）；仓库或库位不存在时自动创建")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "areaName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 库区名称，取用户给出的原文，例如：卷板A区、成品A垛")
        private String areaName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "areaCode")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 库区编码，例如：WH1-A；不填则自动生成")
        private String areaCode;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "warehouseName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 所属仓库名称或编码，例如：卷板仓、WH1。已有同名仓库则复用；不存在则自动创建；完全不提供时创建一个与库区同名的仓库")
        private String warehouseName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "areaSize")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 库区面积（平方米），数字，例如：1800")
        private Double areaSize;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "frozen")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 是否冻结：用户明确说冻结时填 true，否则留空（默认启用）")
        private Boolean frozen;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String areaName = trimToNull(request.getAreaName());
        if (areaName == null) {
            return error("库区创建失败：缺少库区名称，请补充后重试");
        }
        final String warehouseName = trimToNull(request.getWarehouseName());
        final String areaCode = trimToNull(request.getAreaCode());
        final Double areaSize = request.getAreaSize();
        final boolean frozen = Boolean.TRUE.equals(request.getFrozen());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 库区创建 收到请求 tenantId({}) area({}) warehouse({}) areaSize({})]",
                tenantId, areaName, warehouseName, areaSize);
        return executeInTenant(tenantId, "库区创建",
                () -> doCreate(tenantId, areaName, areaCode, warehouseName, areaSize, frozen, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String areaName, String areaCode, String warehouseName,
            Double areaSize, boolean frozen, String remark) {
        // 0. 库区重复拦截（本租户内编码或名称重复）
        List<Map<String, Object>> dupArea = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM mes_wm_warehouse_area"
                        + " WHERE tenant_id = ? AND deleted = 0 AND (name = ? OR (? IS NOT NULL AND code = ?)) LIMIT 1",
                tenantId, areaName, areaCode, areaCode);
        if (!dupArea.isEmpty()) {
            Map<String, Object> hit = dupArea.get(0);
            throw new IllegalArgumentException("库区「" + areaName + "」在系统中已存在（现有记录：" + hit.get("code")
                    + " " + hit.get("name") + "），请勿重复添加");
        }
        // 1. 仓库：按名称/编码解析，不存在则自动创建
        Long warehouseId = resolveOrNull("mes_wm_warehouse", warehouseName);
        boolean warehouseCreated = false;
        String warehouseCode;
        if (warehouseId == null) {
            warehouseCode = (warehouseName != null && warehouseName.matches("[A-Za-z0-9_-]{2,32}"))
                    ? warehouseName : generateCode("WH", null);
            String wName = warehouseName != null ? warehouseName : warehouseCode;
            jdbcTemplate.update("INSERT INTO mes_wm_warehouse (code, name, frozen, remark, creator, updater, deleted, tenant_id)"
                            + " VALUES (?, ?, 0, ?, '1', '1', b'0', ?)",
                    warehouseCode, wName, "AI 助手自动创建（库区录入补层）", tenantId);
            warehouseId = jdbcTemplate.queryForObject(
                    "SELECT id FROM mes_wm_warehouse WHERE code = ? AND tenant_id = ? AND deleted = 0",
                    Long.class, warehouseCode, tenantId);
            warehouseCreated = true;
        } else {
            Map<String, Object> w = jdbcTemplate.queryForMap(
                    "SELECT code, name FROM mes_wm_warehouse WHERE id = ?", warehouseId);
            warehouseCode = (String) w.get("code");
        }
        // 2. 库位：仓下默认库位，不存在则自动创建
        String locationCode = "LOC-" + warehouseCode;
        Long locationId = resolveOrNull("mes_wm_warehouse_location", locationCode);
        if (locationId == null) {
            jdbcTemplate.update("INSERT INTO mes_wm_warehouse_location (code, name, warehouse_id, frozen, remark, creator, updater, deleted, tenant_id)"
                            + " VALUES (?, ?, ?, 0, ?, '1', '1', b'0', ?)",
                    locationCode, warehouseCode + " 库区", warehouseId,
                    "AI 助手自动创建（库区录入补层）", tenantId);
            locationId = jdbcTemplate.queryForObject(
                    "SELECT id FROM mes_wm_warehouse_location WHERE code = ? AND tenant_id = ? AND deleted = 0",
                    Long.class, locationCode, tenantId);
        }
        // 3. 库区
        String finalAreaCode = areaCode != null ? areaCode : generateCode("AREA", null);
        jdbcTemplate.update("INSERT INTO mes_wm_warehouse_area (code, name, location_id, area, frozen, status,"
                        + " allow_item_mixing, allow_batch_mixing, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, 0, 1, 1, ?, '1', '1', b'0', ?)",
                finalAreaCode, areaName, locationId, areaSize, frozen, remark, tenantId);
        // 4. 回读真实落库值
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("库区编码", finalAreaCode);
        record.put("库区名称", areaName);
        record.put("所属仓库", warehouseCode + " " + (warehouseName != null ? warehouseName : warehouseCode)
                + (warehouseCreated ? "（本次自动创建）" : "（已有仓库，直接复用）"));
        record.put("库区面积(㎡)", areaSize != null ? areaSize : "-");
        record.put("冻结状态", frozen ? "已冻结" : "未冻结");
        record.put("备注", remark != null ? remark : "-");
        return ok("库区档案创建成功（可在 MES 仓储管理-库区维护页面查看）", record);
    }

    /** 按编码或名称在本租户内解析记录 id；未找到返回 null（与 resolveSingleton 不同，允许多条时取第一条编码匹配） */
    private Long resolveOrNull(String table, String keyword) {
        if (keyword == null) {
            return null;
        }
        List<Map<String, Object>> hits = jdbcTemplate.queryForList(
                "SELECT id FROM " + table + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?) LIMIT 1",
                cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId(), keyword, keyword);
        return hits.isEmpty() ? null : ((Number) hits.get(0).get("id")).longValue();
    }

}
