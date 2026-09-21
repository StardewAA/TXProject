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
 * MES 工位档案创建工具（Function Calling 写操作）
 *
 * 工位必须归属车间（workshop_id NOT NULL）：车间按名称/编码在本租户内解析，
 * 不存在时不自动创建（车间是产线组织核心，需用户先确认），返回失败原因请用户先建车间。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_workstation_create")
public class MesWorkstationCreateToolFunction extends MesMdCreateSupport<MesWorkstationCreateToolFunction.Request> {

    public MesWorkstationCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 工位档案创建工具：把用户提供的工位信息直接创建为 MES 系统内的真实工位档案（写入数据库）；需提供所属车间")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 工位名称，取用户给出的原文，例如：制管1号机台")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "workshopName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 所属车间名称或编码，必须已存在于系统（不存在时工具会返回失败，请用户先创建车间）")
        private String workshopName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "processName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 关联工序名称，必须已存在于系统；不存在时留空")
        private String processName;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "address")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 位置/地址描述")
        private String address;
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
            return error("工位创建失败：缺少工位名称，请补充后重试");
        }
        if (workshopName == null) {
            return error("工位创建失败：缺少所属车间，请补充车间名称后重试（车间需已存在于系统中）");
        }
        final String code = generateCode("STAT", trimToNull(request.getCode()));
        final String processName = trimToNull(request.getProcessName());
        final String address = trimToNull(request.getAddress());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 工位创建 收到请求 tenantId({}) code({}) name({}) workshop({})]", tenantId, code, name, workshopName);
        return executeInTenant(tenantId, "工位创建",
                () -> doCreate(tenantId, code, name, workshopName, processName, address, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String workshopName,
            String processName, String address, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_workstation", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        // 车间解析（必填依赖）：不存在时不自动创建，请用户先建车间
        List<Map<String, Object>> ws = jdbcTemplate.queryForList(
                "SELECT id, code, name FROM mes_md_workshop"
                        + " WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ? OR REPLACE(name, ' ', '') = ?) LIMIT 2",
                tenantId, workshopName, workshopName, workshopName.replaceAll("[\\s　]+", ""));
        if (ws.isEmpty()) {
            throw new IllegalArgumentException("在本租户档案中未找到车间「" + workshopName
                    + "」，请先创建车间（可直接对我说：新增车间 " + workshopName + "）");
        }
        Long workshopId = ((Number) ws.get(0).get("id")).longValue();
        // 工序解析（可选）
        Long processId = null;
        if (processName != null) {
            List<Map<String, Object>> proc = jdbcTemplate.queryForList(
                    "SELECT id FROM mes_pro_process WHERE tenant_id = ? AND deleted = 0 AND (code = ? OR name = ?) LIMIT 1",
                    tenantId, processName, processName);
            processId = proc.isEmpty() ? null : ((Number) proc.get(0).get("id")).longValue();
        }
        jdbcTemplate.update("INSERT INTO mes_md_workstation (code, name, address, workshop_id, process_id,"
                        + " status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, ?, 0, ?, '1', '1', b'0', ?)",
                code, name, address, workshopId, processId, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("工位编码", code);
        record.put("工位名称", name);
        record.put("所属车间", workshopName);
        record.put("关联工序", processId != null ? processName : "-");
        record.put("位置", address != null ? address : "-");
        record.put("备注", remark != null ? remark : "-");
        return ok("工位档案创建成功（可在 MES 工位维护页面查看）", record);
    }

}
