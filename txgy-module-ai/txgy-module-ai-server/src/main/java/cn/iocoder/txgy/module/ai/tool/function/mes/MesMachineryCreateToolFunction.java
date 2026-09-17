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
 * MES 设备档案创建工具（Function Calling 写操作）
 *
 * 设备必须归属车间（workshop_id NOT NULL）与设备类型（machinery_type_id NOT NULL）：
 * - 车间：本租户解析，不存在时返回失败请先建车间（与工位同策略）
 * - 设备类型：解析不到时自动创建对应类型（简单分类，允许自动补）
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_machinery_create")
public class MesMachineryCreateToolFunction extends MesMdCreateSupport<MesMachineryCreateToolFunction.Request> {

    public MesMachineryCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 设备档案创建工具：把用户提供的设备信息直接创建为 MES 系统内的真实设备档案（写入数据库）；需提供所属车间")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 设备名称，取用户给出的原文，例如：中频加热炉1号")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "workshopName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 所属车间名称或编码，必须已存在于系统")
        private String workshopName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "machineryTypeName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 设备类型名称，例如：加工设备、检测设备；不存在时自动创建该类型")
        private String machineryTypeName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "brand")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 设备品牌/厂家")
        private String brand;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "specification")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 设备规格型号")
        private String specification;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        final String workshopName = trimToNull(request.getWorkshopName());
        if (name == null) {
            return error("设备创建失败：缺少设备名称，请补充后重试");
        }
        if (workshopName == null) {
            return error("设备创建失败：缺少所属车间，请补充车间名称后重试（车间需已存在于系统中）");
        }
        final String code = generateCode("EQ", trimToNull(request.getCode()));
        final String typeName = trimToNull(request.getMachineryTypeName());
        final String brand = trimToNull(request.getBrand());
        final String specification = trimToNull(request.getSpecification());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 设备创建 收到请求 tenantId({}) code({}) name({}) workshop({})]", tenantId, code, name, workshopName);
        return executeInTenant(tenantId, "设备创建",
                () -> doCreate(tenantId, code, name, workshopName, typeName, brand, specification, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String workshopName,
            String typeName, String brand, String specification, String remark) {
        String dup = checkDuplicate(tenantId, "mes_dv_machinery", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        // 车间解析（必填依赖，不自动创建）
        List<Map<String, Object>> ws = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM mes_md_workshop"
                        + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ? OR REPLACE(name, ' ', '') = ?) LIMIT 2",
                tenantId, workshopName, workshopName, workshopName.replaceAll("[\\s　]+", ""));
        if (ws.isEmpty()) {
            throw new IllegalArgumentException("在本租户档案中未找到车间「" + workshopName
                    + "」，请先创建车间（可直接对我说：新增车间 " + workshopName + "）");
        }
        Long workshopId = ((Number) ws.get(0).get("id")).longValue();
        // 设备类型：解析不到自动创建
        String typeDisplay = "-";
        Long typeId = null;
        if (typeName != null) {
            List<Map<String, Object>> types = jdbcTemplate.queryForList(
                    "SELECT id, name FROM mes_dv_machinery_type WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?) LIMIT 1",
                    tenantId, typeName, typeName);
            if (types.isEmpty()) {
                String typeCode = generateCode("MTYP", null);
                jdbcTemplate.update("INSERT INTO mes_dv_machinery_type (code, name, parent_id, status, sort,"
                                + " remark, creator, updater, deleted, tenant_id)"
                                + " VALUES (?, ?, 0, 0, 0, 'AI 助手自动创建', '1', '1', b'0', ?)",
                        typeCode, typeName, tenantId);
                typeId = jdbcTemplate.queryForObject(
                        "SELECT id FROM mes_dv_machinery_type WHERE code = ? AND tenant_id = ? AND deleted = 0",
                        Long.class, typeCode, tenantId);
                typeDisplay = typeName + "（自动创建的类型）";
            } else {
                typeId = ((Number) types.get(0).get("id")).longValue();
                typeDisplay = (String) types.get(0).get("name");
            }
        }
        jdbcTemplate.update("INSERT INTO mes_dv_machinery (code, name, brand, specification, machinery_type_id,"
                        + " workshop_id, status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, ?, 1, ?, '1', '1', b'0', ?)",
                code, name, brand, specification, typeId, workshopId, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("设备编码", code);
        record.put("设备名称", name);
        record.put("所属车间", workshopName);
        record.put("设备类型", typeDisplay);
        record.put("品牌/规格", (brand != null ? brand : "-") + " / " + (specification != null ? specification : "-"));
        record.put("备注", remark != null ? remark : "-");
        return ok("设备档案创建成功（可在 MES 设备管理页面查看）", record);
    }

}
