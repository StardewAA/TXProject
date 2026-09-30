package cn.iocoder.txgy.module.ai.tool.function.mes;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * MES 物料档案创建工具（Function Calling 写操作）
 *
 * 用户给出物料字段（名称/规格/备注）要求新增物料时，直接写入 mes_md_item 表。
 * 编码未给出时自动生成（MT+时间戳）；计量单位/物料类型使用系统默认值（0=未分类），
 * 可在物料档案页面后续调整。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_material_create")
public class MesMaterialCreateToolFunction extends MesMdCreateSupport<MesMaterialCreateToolFunction.Request> {

    public MesMaterialCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 物料档案创建工具：把用户提供的物料信息直接创建为 MES 系统内的真实物料档案（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 物料名称，取用户给出的名称原文，例如：防腐保温管 DN50、环氧粉末")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "specification")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 规格型号，例如：DN50*3mm、25kg/袋")
        private String specification;
    }

    @Override
    public Map<String, Object> apply(Request request) {
        Long tenantId = currentTenant();
        if (tenantId == null) {
            return error("无法识别当前用户所属租户，拒绝创建");
        }
        final String name = trimToNull(request.getName());
        if (name == null) {
            return error("物料创建失败：缺少物料名称，请补充后重试");
        }
        final String code = generateCode("MT", trimToNull(request.getCode()));
        final String specification = trimToNull(request.getSpecification());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 物料创建 收到请求 tenantId({}) code({}) name({})]", tenantId, code, name);
        return executeInTenant(tenantId, "物料创建", () -> doCreate(tenantId, code, name, specification, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String specification, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_item", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        jdbcTemplate.update("INSERT INTO mes_md_item (code, name, specification,"
                        + " unit_measure_id, item_type_id, status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, 0, 0, 0, ?, '1', '1', b'0', ?)",
                code, name, specification, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("物料编码", code);
        record.put("物料名称", name);
        record.put("规格型号", specification != null ? specification : "-");
        record.put("计量单位/物料类型", "未分类（可在物料档案页面调整）");
        record.put("备注", remark != null ? remark : "-");
        return ok("物料档案创建成功（可在 MES 物料档案页面查看与完善计量单位、类型、库存上下限）", record);
    }

}
