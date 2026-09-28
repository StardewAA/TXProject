package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 计量单位创建工具（Function Calling 写操作）
 *
 * 用户给出计量单位（个/千克/米等）要求新增时，直接写入 mes_md_unit_measure 表，
 * 供物料档案引用。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_unit_measure_create")
public class MesUnitMeasureCreateToolFunction extends MesMdCreateSupport<MesUnitMeasureCreateToolFunction.Request> {

    public MesUnitMeasureCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 计量单位创建工具：把用户提供的计量单位直接创建为 MES 系统内的真实计量单位（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 计量单位名称，例如：个、千克、米、卷")
        private String name;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("计量单位创建失败：缺少单位名称，请补充后重试");
        }
        final String code = generateCode("UM", trimToNull(request.getCode()));
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 计量单位创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "计量单位创建", () -> doCreate(tenantId, code, name, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_unit_measure", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        jdbcTemplate.update("INSERT INTO mes_md_unit_measure (code, name, status,"
                        + " remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, 0, ?, '1', '1', b'0', ?)",
                code, name, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("单位编码", code);
        record.put("单位名称", name);
        record.put("备注", remark != null ? remark : "-");
        return ok("计量单位创建成功（可在 MES 计量单位页面查看）", record);
    }

}
