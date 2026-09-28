package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 工序档案创建工具（Function Calling 写操作）
 *
 * 用户给出工序信息要求新增工序时，直接写入 mes_pro_process 表。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_process_create")
public class MesProcessCreateToolFunction extends MesMdCreateSupport<MesProcessCreateToolFunction.Request> {

    public MesProcessCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 工序档案创建工具：把用户提供的工序信息直接创建为 MES 系统内的真实工序档案（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 工序名称，取用户给出的原文，例如：酸洗、钝化、探伤")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "attention")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 注意事项/操作要点")
        private String attention;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("工序创建失败：缺少工序名称，请补充后重试");
        }
        final String code = generateCode("PROC", trimToNull(request.getCode()));
        final String attention = trimToNull(request.getAttention());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 工序创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "工序创建", () -> doCreate(tenantId, code, name, attention, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String attention, String remark) {
        String dup = checkDuplicate(tenantId, "mes_pro_process", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        jdbcTemplate.update("INSERT INTO mes_pro_process (code, name, attention, status,"
                        + " remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, 0, ?, '1', '1', b'0', ?)",
                code, name, attention, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("工序编码", code);
        record.put("工序名称", name);
        record.put("注意事项", attention != null ? attention : "-");
        record.put("备注", remark != null ? remark : "-");
        return ok("工序档案创建成功（可在 MES 工序维护页面查看）", record);
    }

}
