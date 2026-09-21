package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 班组档案创建工具（Function Calling 写操作）
 *
 * 用户给出班组信息要求新增班组时，直接写入 mes_cal_team 表。
 * 成员维护需在排班-班组管理页面补充（涉及用户关联，不在本工具范围）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_team_create")
public class MesTeamCreateToolFunction extends MesMdCreateSupport<MesTeamCreateToolFunction.Request> {

    public MesTeamCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 班组档案创建工具：把用户提供的班组信息直接创建为 MES 系统内的真实班组档案（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 班组名称，取用户给出的原文，例如：注塑A组、白班一组")
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
            return error("班组创建失败：缺少班组名称，请补充后重试");
        }
        final String code = generateCode("TEAM", trimToNull(request.getCode()));
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 班组创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "班组创建", () -> doCreate(tenantId, code, name, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String remark) {
        String dup = checkDuplicate(tenantId, "mes_cal_team", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        jdbcTemplate.update("INSERT INTO mes_cal_team (code, name, calendar_type,"
                        + " remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, NULL, ?, '1', '1', b'0', ?)",
                code, name, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("班组编码", code);
        record.put("班组名称", name);
        record.put("备注", remark != null ? remark : "-");
        return ok("班组档案创建成功（班组排班类型与成员请在 MES 排班-班组管理页面补充）", record);
    }

}
