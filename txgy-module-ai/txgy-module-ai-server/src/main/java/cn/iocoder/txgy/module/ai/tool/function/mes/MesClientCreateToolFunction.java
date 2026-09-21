package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 客户档案创建工具（Function Calling 写操作）
 *
 * 用户给出客户信息要求新增客户时，直接写入 mes_md_client 表（type=1 客户）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_client_create")
public class MesClientCreateToolFunction extends MesMdCreateSupport<MesClientCreateToolFunction.Request> {

    public MesClientCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 客户档案创建工具：把用户提供的客户信息直接创建为 MES 系统内的真实客户档案（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 客户名称/公司名，取用户给出的原文，例如：博世、比亚迪股份有限公司")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "address")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 地址")
        private String address;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "telephone")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 联系电话")
        private String telephone;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "contactName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 联系人姓名")
        private String contactName;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("客户创建失败：缺少客户名称，请补充后重试");
        }
        final String code = generateCode("C", trimToNull(request.getCode()));
        final String address = trimToNull(request.getAddress());
        final String telephone = trimToNull(request.getTelephone());
        final String contactName = trimToNull(request.getContactName());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 客户创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "客户创建", () -> doCreate(tenantId, code, name, address, telephone, contactName, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String address,
            String telephone, String contactName, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_client", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        jdbcTemplate.update("INSERT INTO mes_md_client (code, name, type, address, telephone,"
                        + " contact1_name, remark, status, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, 1, ?, ?, ?, ?, 0, '1', '1', b'0', ?)",
                code, name, address, telephone, contactName, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("客户编码", code);
        record.put("客户名称", name);
        record.put("地址", address != null ? address : "-");
        record.put("联系电话", telephone != null ? telephone : "-");
        record.put("联系人", contactName != null ? contactName : "-");
        record.put("备注", remark != null ? remark : "-");
        return ok("客户档案创建成功（可在 MES 客户档案页面查看）", record);
    }

}
