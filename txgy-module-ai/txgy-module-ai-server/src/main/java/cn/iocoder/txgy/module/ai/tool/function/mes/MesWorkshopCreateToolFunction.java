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
 * MES 车间档案创建工具（Function Calling 写操作）
 *
 * 用户给出车间信息要求新增车间时，直接写入 mes_md_workshop 表。
 * 负责人需在本租户系统用户内按用户名解析（无法解析时留空并提示可页面补充）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_workshop_create")
public class MesWorkshopCreateToolFunction extends MesMdCreateSupport<MesWorkshopCreateToolFunction.Request> {

    public MesWorkshopCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 车间档案创建工具：把用户提供的车间信息直接创建为 MES 系统内的真实车间档案（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 车间名称，取用户给出的原文，例如：制管车间、防腐车间")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "areaSize")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 车间面积（平方米），数字")
        private Double areaSize;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "chargeUserName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 负责人用户名/昵称，系统在本租户用户内解析；解析不到会留空并提示可页面补充")
        private String chargeUserName;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("车间创建失败：缺少车间名称，请补充后重试");
        }
        final String code = generateCode("WS", trimToNull(request.getCode()));
        final Double areaSize = request.getAreaSize();
        final String chargeUserName = trimToNull(request.getChargeUserName());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 车间创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "车间创建", () -> doCreate(tenantId, code, name, areaSize, chargeUserName, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, Double areaSize,
            String chargeUserName, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_workshop", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        // 负责人解析（可选）：系统用户表按用户名/昵称精确匹配
        Long chargeUserId = null;
        String chargeUserDisplay = "-";
        if (chargeUserName != null) {
            List<Map<String, Object>> users = jdbcTemplate.queryForList(
                    "SELECT id, username, nickname FROM system_users"
                            + " WHERE tenant_id = ? AND deleted = 0 AND (username = ? OR nickname = ?) LIMIT 1",
                    tenantId, chargeUserName, chargeUserName);
            if (!users.isEmpty()) {
                chargeUserId = ((Number) users.get(0).get("id")).longValue();
                chargeUserDisplay = chargeUserName;
            }
        }
        jdbcTemplate.update("INSERT INTO mes_md_workshop (code, name, area, charge_user_id, status,"
                        + " remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, ?, 0, ?, '1', '1', b'0', ?)",
                code, name, areaSize, chargeUserId, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("车间编码", code);
        record.put("车间名称", name);
        record.put("车间面积(㎡)", areaSize != null ? areaSize : "-");
        record.put("负责人", chargeUserId != null ? chargeUserDisplay
                : (chargeUserName != null ? "未找到用户「" + chargeUserName + "」，已留空，可在车间维护页面补充" : "-"));
        record.put("备注", remark != null ? remark : "-");
        return ok("车间档案创建成功（可在 MES 基础数据-车间/产线维护页面查看）", record);
    }

}
