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
 * MES 设备类型创建工具（Function Calling 写操作）
 *
 * 用户给出设备类型（分类）要求新增时，直接写入 mes_dv_machinery_type 表；
 * 支持指定父类型（不存在时创建为顶级）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_machinery_type_create")
public class MesMachineryTypeCreateToolFunction extends MesMdCreateSupport<MesMachineryTypeCreateToolFunction.Request> {

    public MesMachineryTypeCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 设备类型创建工具：把用户提供的设备类型（分类）直接创建为 MES 系统内的真实设备类型（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 设备类型名称，取用户给出的原文，例如：加工设备、检测设备、起重设备")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "parentName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 父类型名称，作为子类型时填写；父类型不存在时创建为顶级类型")
        private String parentName;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("设备类型创建失败：缺少类型名称，请补充后重试");
        }
        final String code = generateCode("MTYP", trimToNull(request.getCode()));
        final String parentName = trimToNull(request.getParentName());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 设备类型创建 收到请求 tenantId({}) code({}) name({}) parent({})]", tenantId, code, name, parentName);
        return executeInTenant(tenantId, "设备类型创建", () -> doCreate(tenantId, code, name, parentName, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String parentName, String remark) {
        String dup = checkDuplicate(tenantId, "mes_dv_machinery_type", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        long parentId = 0L;
        String parentDisplay = "顶级类型";
        if (parentName != null) {
            List<Map<String, Object>> parents = jdbcTemplate.queryForList(
                    "SELECT id, name FROM mes_dv_machinery_type"
                            + " WHERE tenant_id = ? AND deleted = 0 AND (name = ? OR REPLACE(name, ' ', '') = ?) LIMIT 1",
                    tenantId, parentName, parentName.replaceAll("[\\s　]+", ""));
            if (!parents.isEmpty()) {
                parentId = ((Number) parents.get(0).get("id")).longValue();
                parentDisplay = parents.get(0).get("name") + " 的子类型";
            }
        }
        jdbcTemplate.update("INSERT INTO mes_dv_machinery_type (code, name, parent_id, status, sort,"
                        + " remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, 0, 0, ?, '1', '1', b'0', ?)",
                code, name, parentId, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("类型编码", code);
        record.put("类型名称", name);
        record.put("层级", parentDisplay);
        record.put("备注", remark != null ? remark : "-");
        return ok("设备类型创建成功（可在 MES 设备管理-设备类型页面查看）", record);
    }

}
