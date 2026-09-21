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
 * MES 物料类型创建工具（Function Calling 写操作）
 *
 * 用户给出物料类型（分类）要求新增时，直接写入 mes_md_item_type 表。
 * item_or_product 固定 ITEM（物料分类）；支持指定父分类（不存在时创建为顶级）。
 *
 * @author 天信管业
 */
@Slf4j
@Component("mes_item_type_create")
public class MesItemTypeCreateToolFunction extends MesMdCreateSupport<MesItemTypeCreateToolFunction.Request> {

    public MesItemTypeCreateToolFunction(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @lombok.Data
    @com.fasterxml.jackson.annotation.JsonClassDescription("MES 物料类型创建工具：把用户提供的物料分类直接创建为 MES 系统内的真实物料类型（写入数据库）")
    public static class Request extends BaseRequest {

        @com.fasterxml.jackson.annotation.JsonProperty(value = "name")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[必填] 物料类型名称，例如：原材料、辅料、成品")
        private String name;

        @com.fasterxml.jackson.annotation.JsonProperty(value = "parentName")
        @com.fasterxml.jackson.annotation.JsonPropertyDescription("[可选] 父分类名称，作为子分类时填写；父分类不存在时本类型将创建为顶级分类")
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
            return error("物料类型创建失败：缺少类型名称，请补充后重试");
        }
        final String code = generateCode("IT", trimToNull(request.getCode()));
        final String parentName = trimToNull(request.getParentName());
        final String remark = trimToNull(request.getRemark());
        log.info("[apply][MES 物料类型创建 收到请求 tenantId({}) code({}) name({}) parent({})]", tenantId, code, name, parentName);
        return executeInTenant(tenantId, "物料类型创建", () -> doCreate(tenantId, code, name, parentName, remark));
    }

    private Map<String, Object> doCreate(Long tenantId, String code, String name, String parentName, String remark) {
        String dup = checkDuplicate(tenantId, "mes_md_item_type", code, name);
        if (dup != null) {
            throw new IllegalArgumentException(dup);
        }
        long parentId = 0L;
        String parentDisplay = "顶级分类";
        if (parentName != null) {
            List<Map<String, Object>> parents = jdbcTemplate.queryForList(
                    "SELECT id, name FROM mes_md_item_type"
                            + " WHERE tenant_id = ? AND deleted = 0 AND (name = ? OR REPLACE(name, ' ', '') = ?) LIMIT 1",
                    tenantId, parentName, parentName.replaceAll("[\\s　]+", ""));
            if (!parents.isEmpty()) {
                parentId = ((Number) parents.get(0).get("id")).longValue();
                parentDisplay = parents.get(0).get("name") + " 的子分类";
            }
        }
        jdbcTemplate.update("INSERT INTO mes_md_item_type (code, name, parent_id, item_or_product, sort,"
                        + " status, remark, creator, updater, deleted, tenant_id)"
                        + " VALUES (?, ?, ?, 'ITEM', 0, 0, ?, '1', '1', b'0', ?)",
                code, name, parentId, remark, tenantId);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("类型编码", code);
        record.put("类型名称", name);
        record.put("层级", parentDisplay);
        record.put("备注", remark != null ? remark : "-");
        return ok("物料类型创建成功（可在 MES 物料类型页面查看）", record);
    }

}
