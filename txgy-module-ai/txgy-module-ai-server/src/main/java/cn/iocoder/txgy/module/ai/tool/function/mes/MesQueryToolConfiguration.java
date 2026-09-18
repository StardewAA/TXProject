package cn.iocoder.txgy.module.ai.tool.function.mes;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MES 业务域查询工具装配（13 个业务域，与 AiMesBizTypeEnum 的 toolName 一一对应）
 *
 * 权限链路：ai_chat_role.tool_ids → AiPermissionGuard.filterAllowedTools（按授权业务域过滤）
 * → 工具注入大模型 → 模型 Function Calling 调用 → 本类只读 SQL 查询真实数据
 *
 * 数据原则：所有记录来自 MES 数据库实时查询，状态码在 SQL 中翻译为中文，模型只做整理转述。
 *
 * [参数绑定约定] keywordOrFilter 生成的每组占位符为 (? IS NULL OR col LIKE ?)，
 * 绑定顺序为先 IS NULL 检查值（keyword 原文）、再 LIKE 值（like(keyword) 带通配符），
 * 统一由 {@link #likeArgs(String, int)} 按列数生成，避免各工具手写错位。
 *
 * @author 天信管业
 */
@Configuration
public class MesQueryToolConfiguration {

    /**
     * 多列关键词过滤 SQL 片段（OR 语义）
     *
     * <p>[根因修复] 原实现把 {@code (? IS NULL OR col LIKE ?)} 按列 AND 拼接多段，
     * keyword 必须同时命中所有列才返回；实际用户常只点名单列（如按物料名查库存），
     * AND 组合恒为 0 行，误报「未查询到相关数据」。改为 OR 语义后：
     * 任一列命中即返回；keyword 为空时每组 {@code ? IS NULL} 短路为 true，不设过滤。
     *
     * @param columns 参与匹配的列（如 it.code、it.name、wh.name）
     */
    private static String keywordOrFilter(String... columns) {
        StringBuilder sb = new StringBuilder(" AND ((");
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sb.append(" OR ");
            }
            sb.append("(? IS NULL OR ").append(columns[i]).append(" LIKE ?)");
        }
        return sb.append("))").toString();
    }

    /**
     * 生成 keywordOrFilter 对应的绑定参数段：每列先绑 IS NULL 检查值（keyword 原文）、
     * 再绑 LIKE 值（like(keyword) 带通配符）；keyword 为空时 LIKE 值绑 null（由 IS NULL 短路）
     */
    private static Object[] likeArgs(String keyword, int columns) {
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            args.add(keyword);
            args.add(keyword != null ? MesBaseQueryToolFunction.like(keyword) : null);
        }
        return args.toArray();
    }

    // ==================== 生产制造域 ====================

    /**
     * 生产工单查询（对应业务域 PRO_WORK_ORDER）
     */
    @Bean("mes_work_order_query")
    public MesBaseQueryToolFunction workOrderQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "生产工单") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "草稿:0,已确认:1,已完成:2,已取消:3");
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT wo.code AS 工单编号, wo.name AS 名称, IFNULL(it.name,'') AS 产品, IFNULL(cl.name,'') AS 客户,"
                        + " wo.quantity + 0 AS 计划数量, wo.quantity_produced + 0 AS 已生产数量,"
                        + " DATE(wo.request_date) AS 要求交期,"
                        + " CASE wo.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '已完成' WHEN 3 THEN '已取消' ELSE '未知' END AS 状态"
                        + " FROM mes_pro_work_order wo"
                        + " LEFT JOIN mes_md_item it ON it.id = wo.product_id"
                        + " LEFT JOIN mes_md_client cl ON cl.id = wo.client_id"
                        + " WHERE wo.tenant_id = ? AND wo.deleted = 0"
                        + keywordOrFilter("wo.code", "wo.name", "it.name")
                        + " AND (? IS NULL OR wo.status = ?)"
                        + " ORDER BY wo.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 生产任务查询（对应业务域 PRO_TASK）
     */
    @Bean("mes_task_query")
    public MesBaseQueryToolFunction taskQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "生产任务") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "草稿:0,已完成:4,已取消:5");
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT t.code AS 任务编号, t.name AS 名称, IFNULL(wo.code,'') AS 所属工单, IFNULL(it.name,'') AS 物料,"
                        + " IFNULL(ws.name,'') AS 工位, t.quantity + 0 AS 计划数量, t.produced_quantity + 0 AS 已生产数量,"
                        + " t.qualify_quantity + 0 AS 合格数量, t.unqualify_quantity + 0 AS 不合格数量,"
                        + " IFNULL(DATE(t.start_time),'未开工') AS 开工时间, IFNULL(DATE(t.end_time),'未完工') AS 完工时间,"
                        + " CASE t.status WHEN 0 THEN '草稿' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', t.status) END AS 状态"
                        + " FROM mes_pro_task t"
                        + " LEFT JOIN mes_pro_work_order wo ON wo.id = t.work_order_id"
                        + " LEFT JOIN mes_md_item it ON it.id = t.item_id"
                        + " LEFT JOIN mes_md_workstation ws ON ws.id = t.workstation_id"
                        + " WHERE t.tenant_id = ? AND t.deleted = 0"
                        + keywordOrFilter("t.code", "t.name", "it.name")
                        + " AND (? IS NULL OR t.status = ?)"
                        + " ORDER BY t.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 生产报工查询（对应业务域 PRO_FEEDBACK）
     */
    @Bean("mes_feedback_query")
    public MesBaseQueryToolFunction feedbackQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "生产报工") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "草稿:0,审批中:2,待检验:3,已完成:4");
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 2)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT f.code AS 报工单号, IFNULL(wo.code,'') AS 所属工单, IFNULL(it.name,'') AS 物料,"
                        + " f.feedback_quantity + 0 AS 报工数量, f.qualified_quantity + 0 AS 合格数量, f.unqualified_quantity + 0 AS 不合格数量,"
                        + " IFNULL(f.feedback_time,'') AS 报工时间,"
                        + " CASE f.status WHEN 0 THEN '草稿' WHEN 2 THEN '审批中' WHEN 3 THEN '待检验' WHEN 4 THEN '已完成' ELSE CONCAT('状态码', f.status) END AS 状态"
                        + " FROM mes_pro_feedback f"
                        + " LEFT JOIN mes_pro_work_order wo ON wo.id = f.work_order_id"
                        + " LEFT JOIN mes_md_item it ON it.id = f.item_id"
                        + " WHERE f.tenant_id = ? AND f.deleted = 0"
                        + keywordOrFilter("f.code", "it.name")
                        + " AND (? IS NULL OR f.status = ?)"
                        + " ORDER BY f.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 安灯异常查询（对应业务域 PRO_ANDON）
     *
     * <p>数据源 mes_pro_andon_record（只读）。状态口径引用冻结枚举 MesProAndonStatusEnum：
     * 0=未处置(ACTIVE)、1=已处置(HANDLED)；等级引用 MesProAndonLevelEnum：1 一级 / 2 二级 / 3 三级。
     */
    @Bean("mes_andon_query")
    public MesBaseQueryToolFunction andonQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "安灯异常") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "未处置:0,已处置:1");
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT ar.id + 0 AS 安灯编号, IFNULL(ws.name,'') AS 工位, IFNULL(wo.code,'') AS 关联工单,"
                        + " ar.reason AS 异常原因,"
                        + " CASE ar.level WHEN 1 THEN '一级' WHEN 2 THEN '二级' WHEN 3 THEN '三级' ELSE CONCAT('等级', ar.level) END AS 异常等级,"
                        + " CASE ar.status WHEN 0 THEN '未处置' WHEN 1 THEN '已处置' ELSE CONCAT('状态码', ar.status) END AS 状态,"
                        + " IFNULL(DATE_FORMAT(ar.create_time,'%Y-%m-%d %H:%i'),'') AS 触发时间,"
                        + " IFNULL(DATE_FORMAT(ar.handle_time,'%Y-%m-%d %H:%i'),'未处置') AS 处置时间"
                        + " FROM mes_pro_andon_record ar"
                        + " LEFT JOIN mes_md_workstation ws ON ws.id = ar.workstation_id"
                        + " LEFT JOIN mes_pro_work_order wo ON wo.id = ar.work_order_id"
                        + " WHERE ar.tenant_id = ? AND ar.deleted = 0"
                        + keywordOrFilter("ar.reason", "ws.name", "wo.code")
                        + " AND (? IS NULL OR ar.status = ?)"
                        + " ORDER BY ar.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 待检任务查询（对应业务域 PRO_PENDING）
     *
     * <p>数据源 mes_pro_task（只读）。任务状态数值引用 MesOrderStatusConstants
     * （0 草稿 / 1 已确认 / 2 审批中 / 3 已审批 / 4 已完成 / 5 已取消），
     * 注意与工单状态枚举数值不同，不得混用。
     * 待办口径：用户未指定状态时默认只返回「已确认(1)」的未开工/待执行任务。
     */
    @Bean("mes_pending_query")
    public MesBaseQueryToolFunction pendingQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "待检任务") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status,
                        "已确认:1,待生产:1,待检:1,待检验:1,审批中:2,已审批:3,已完成:4,已取消:5");
                if (statusCode == null) {
                    // 待办口径默认值：只看待执行任务
                    statusCode = 1;
                }
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT t.code AS 任务编号, t.name AS 任务名称, IFNULL(wo.code,'') AS 所属工单,"
                        + " IFNULL(it.name,'') AS 物料, IFNULL(ws.name,'') AS 工位,"
                        + " t.quantity + 0 AS 计划数量, t.produced_quantity + 0 AS 已生产数量,"
                        + " IFNULL(DATE(t.start_time),'未开工') AS 开工时间,"
                        + " CASE t.status WHEN 0 THEN '草稿' WHEN 1 THEN '已确认' WHEN 2 THEN '审批中' WHEN 3 THEN '已审批'"
                        + " WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', t.status) END AS 状态"
                        + " FROM mes_pro_task t"
                        + " LEFT JOIN mes_pro_work_order wo ON wo.id = t.work_order_id"
                        + " LEFT JOIN mes_md_item it ON it.id = t.item_id"
                        + " LEFT JOIN mes_md_workstation ws ON ws.id = t.workstation_id"
                        + " WHERE t.tenant_id = ? AND t.deleted = 0"
                        + keywordOrFilter("t.code", "t.name", "it.name")
                        + " AND (? IS NULL OR t.status = ?)"
                        + " ORDER BY t.id ASC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 工艺工序查询（对应业务域 PRO_PROCESS）
     */
    @Bean("mes_process_query")
    public MesBaseQueryToolFunction processQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "工艺工序") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 2)));
                args.add(limit);
                String sql = "SELECT p.code AS 工序编码, p.name AS 工序名称, IFNULL(p.attention,'') AS 注意事项,"
                        + " CASE p.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                        + " FROM mes_pro_process p"
                        + " WHERE p.tenant_id = ? AND p.deleted = 0"
                        + keywordOrFilter("p.code", "p.name")
                        + " ORDER BY p.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 物料产品查询（对应业务域 MD_ITEM）
     */
    @Bean("mes_item_query")
    public MesBaseQueryToolFunction itemQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "物料产品") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(limit);
                String sql = "SELECT i.code AS 物料编码, i.name AS 物料名称, IFNULL(i.specification,'') AS 规格型号,"
                        + " IFNULL(t.name,'未分类') AS 物料分类, IFNULL(u.name,'') AS 单位,"
                        + " i.min_stock + 0 AS 安全库存下限, i.max_stock + 0 AS 安全库存上限,"
                        + " CASE i.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                        + " FROM mes_md_item i"
                        + " LEFT JOIN mes_md_item_type t ON t.id = i.item_type_id"
                        + " LEFT JOIN mes_md_unit_measure u ON u.id = i.unit_measure_id"
                        + " WHERE i.tenant_id = ? AND i.deleted = 0"
                        + keywordOrFilter("i.code", "i.name", "i.specification")
                        + " ORDER BY i.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    /**
     * 车间工位查询（对应业务域 MD_WORKSHOP）：同时返回车间与工位两组数据
     */
    @Bean("mes_workshop_query")
    public MesBaseQueryToolFunction workshopQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "车间工位") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("车间列表", jdbcTemplate.queryForList(
                        "SELECT w.code AS 车间编码, w.name AS 车间名称, w.area + 0 AS 面积,"
                                + " CASE w.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                                + " FROM mes_md_workshop w WHERE w.tenant_id = ? AND w.deleted = 0"
                                + " AND (? IS NULL OR w.code LIKE ? OR w.name LIKE ?) ORDER BY w.id LIMIT ?",
                        tenantId, keyword, keyword != null ? like(keyword) : null, keyword != null ? like(keyword) : null, limit));
                result.put("工位列表", jdbcTemplate.queryForList(
                        "SELECT s.code AS 工位编码, s.name AS 工位名称, IFNULL(w.name,'未分配') AS 所属车间,"
                                + " CASE s.status WHEN 0 THEN '启用' WHEN 1 THEN '停用' ELSE '未知' END AS 状态"
                                + " FROM mes_md_workstation s LEFT JOIN mes_md_workshop w ON w.id = s.workshop_id"
                                + " WHERE s.tenant_id = ? AND s.deleted = 0"
                                + " AND (? IS NULL OR s.code LIKE ? OR s.name LIKE ? OR w.name LIKE ?) ORDER BY s.id LIMIT ?",
                        tenantId, keyword, keyword != null ? like(keyword) : null, keyword != null ? like(keyword) : null,
                        keyword != null ? like(keyword) : null, limit));
                return List.of(result);
            }
        };
    }

    // ==================== 质量域 ====================

    /**
     * 质量检验查询（对应业务域 QC_INSPECT）：一次返回 IQC / IPQC / OQC 三类检验单
     */
    @Bean("mes_qc_query")
    public MesBaseQueryToolFunction qcQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "质量检验") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "草稿:0,已完成:4,已取消:5");
                Map<String, Object> result = new LinkedHashMap<>();
                // 每类检验单的过滤组为 3 个占位符：kw(IS NULL) + like(code) + like(it.name)
                List<Object> iqcArgs = new ArrayList<>();
                iqcArgs.add(tenantId);
                iqcArgs.add(keyword);
                iqcArgs.add(keyword != null ? like(keyword) : null);
                iqcArgs.add(keyword != null ? like(keyword) : null);
                iqcArgs.add(statusCode);
                iqcArgs.add(statusCode);
                iqcArgs.add(limit);
                result.put("来料检验IQC", jdbcTemplate.queryForList(
                        "SELECT q.code AS 检验单号, IFNULL(v.name,'') AS 供应商, IFNULL(it.name,'') AS 物料,"
                                + " q.received_quantity + 0 AS 来料数量, IFNULL(q.check_quantity + 0, 0) AS 检验数量,"
                                + " IFNULL(q.qualified_quantity + 0, 0) AS 合格数量, IFNULL(q.unqualified_quantity + 0, 0) AS 不合格数量,"
                                + " CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END AS 结论,"
                                + " CASE q.status WHEN 0 THEN '草稿' WHEN 4 THEN '已完成' WHEN 5 THEN '已取消' ELSE CONCAT('状态码', q.status) END AS 状态"
                                + " FROM mes_qc_iqc q LEFT JOIN mes_md_vendor v ON v.id = q.vendor_id LEFT JOIN mes_md_item it ON it.id = q.item_id"
                                + " WHERE q.tenant_id = ? AND q.deleted = 0"
                                + " AND (? IS NULL OR q.code LIKE ? OR it.name LIKE ?)"
                                + " AND (? IS NULL OR q.status = ?) ORDER BY q.id DESC LIMIT ?",
                        iqcArgs.toArray()));
                List<Object> ipqcArgs = new ArrayList<>();
                ipqcArgs.add(tenantId);
                ipqcArgs.add(keyword);
                ipqcArgs.add(keyword != null ? like(keyword) : null);
                ipqcArgs.add(keyword != null ? like(keyword) : null);
                ipqcArgs.add(statusCode);
                ipqcArgs.add(statusCode);
                ipqcArgs.add(limit);
                result.put("过程检验IPQC", jdbcTemplate.queryForList(
                        "SELECT q.code AS 检验单号, CASE q.type WHEN 1 THEN '自制工序检验' WHEN 2 THEN '首检' WHEN 3 THEN '巡检' WHEN 4 THEN '自检' WHEN 5 THEN '成品检验' ELSE CONCAT('类型码', q.type) END AS 检验类型,"
                                + " IFNULL(wo.code,'') AS 所属工单, IFNULL(it.name,'') AS 物料,"
                                + " IFNULL(q.check_quantity + 0, 0) AS 检验数量, IFNULL(q.qualified_quantity + 0, 0) AS 合格数量, IFNULL(q.unqualified_quantity + 0, 0) AS 不合格数量,"
                                + " CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END AS 结论"
                                + " FROM mes_qc_ipqc q LEFT JOIN mes_pro_work_order wo ON wo.id = q.work_order_id LEFT JOIN mes_md_item it ON it.id = q.item_id"
                                + " WHERE q.tenant_id = ? AND q.deleted = 0"
                                + " AND (? IS NULL OR q.code LIKE ? OR it.name LIKE ?)"
                                + " AND (? IS NULL OR q.status = ?) ORDER BY q.id DESC LIMIT ?",
                        ipqcArgs.toArray()));
                List<Object> oqcArgs = new ArrayList<>();
                oqcArgs.add(tenantId);
                oqcArgs.add(keyword);
                oqcArgs.add(keyword != null ? like(keyword) : null);
                oqcArgs.add(keyword != null ? like(keyword) : null);
                oqcArgs.add(statusCode);
                oqcArgs.add(statusCode);
                oqcArgs.add(limit);
                result.put("出货检验OQC", jdbcTemplate.queryForList(
                        "SELECT q.code AS 检验单号, IFNULL(c.name,'') AS 客户, IFNULL(it.name,'') AS 物料,"
                                + " q.out_quantity + 0 AS 出货数量, IFNULL(q.check_quantity + 0, 0) AS 检验数量,"
                                + " IFNULL(q.qualified_quantity + 0, 0) AS 合格数量, IFNULL(q.unqualified_quantity + 0, 0) AS 不合格数量,"
                                + " CASE q.check_result WHEN 1 THEN '校验通过' WHEN 2 THEN '校验不通过' ELSE '未检' END AS 结论"
                                + " FROM mes_qc_oqc q LEFT JOIN mes_md_client c ON c.id = q.client_id LEFT JOIN mes_md_item it ON it.id = q.item_id"
                                + " WHERE q.tenant_id = ? AND q.deleted = 0"
                                + " AND (? IS NULL OR q.code LIKE ? OR it.name LIKE ?)"
                                + " AND (? IS NULL OR q.status = ?) ORDER BY q.id DESC LIMIT ?",
                        oqcArgs.toArray()));
                return List.of(result);
            }
        };
    }

    // ==================== 设备域 ====================

    /**
     * 设备台账查询（对应业务域 DV_MACHINERY）
     */
    @Bean("mes_machinery_query")
    public MesBaseQueryToolFunction machineryQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "设备台账") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Integer statusCode = mapStatus(status, "停机:1,生产中:2,维护中:3");
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(statusCode);
                args.add(statusCode);
                args.add(limit);
                String sql = "SELECT m.code AS 设备编码, m.name AS 设备名称, IFNULL(m.brand,'') AS 品牌,"
                        + " IFNULL(mt.name,'未分类') AS 设备类型, IFNULL(w.name,'未分配') AS 所在车间,"
                        + " CASE m.status WHEN 1 THEN '停机' WHEN 2 THEN '生产中' WHEN 3 THEN '维护中' ELSE CONCAT('状态码', m.status) END AS 状态,"
                        + " IFNULL(DATE(m.last_check_time),'未点检') AS 最近点检时间, IFNULL(DATE(m.last_mainten_time),'未保养') AS 最近保养时间"
                        + " FROM mes_dv_machinery m"
                        + " LEFT JOIN mes_dv_machinery_type mt ON mt.id = m.machinery_type_id"
                        + " LEFT JOIN mes_md_workshop w ON w.id = m.workshop_id"
                        + " WHERE m.tenant_id = ? AND m.deleted = 0"
                        + keywordOrFilter("m.code", "m.name", "m.brand")
                        + " AND (? IS NULL OR m.status = ?)"
                        + " ORDER BY m.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    // ==================== 仓储域 ====================

    /**
     * 仓储库存查询（对应业务域 WM_STOCK）
     */
    @Bean("mes_stock_query")
    public MesBaseQueryToolFunction stockQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "仓储库存") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 3)));
                args.add(limit);
                String sql = "SELECT it.code AS 物料编码, it.name AS 物料名称, IFNULL(wh.name,'') AS 仓库,"
                        + " IFNULL(ms.batch_code,'') AS 批次, ms.quantity + 0 AS 库存数量,"
                        + " IFNULL(DATE(ms.receipt_time),'') AS 入库时间"
                        + " FROM mes_wm_material_stock ms"
                        + " LEFT JOIN mes_md_item it ON it.id = ms.item_id"
                        + " LEFT JOIN mes_wm_warehouse wh ON wh.id = ms.warehouse_id"
                        + " WHERE ms.tenant_id = ? AND ms.deleted = 0"
                        + keywordOrFilter("it.code", "it.name", "wh.name")
                        + " ORDER BY ms.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    // ==================== 排班域 ====================

    /**
     * 排班班组查询（对应业务域 CAL_TEAM）
     */
    @Bean("mes_team_query")
    public MesBaseQueryToolFunction teamQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "排班班组") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                List<Object> args = new ArrayList<>();
                args.add(tenantId);
                args.addAll(Arrays.asList(likeArgs(keyword, 2)));
                args.add(limit);
                String sql = "SELECT t.code AS 班组编码, t.name AS 班组名称"
                        + " FROM mes_cal_team t"
                        + " WHERE t.tenant_id = ? AND t.deleted = 0"
                        + keywordOrFilter("t.code", "t.name")
                        + " ORDER BY t.id DESC LIMIT ?";
                return jdbcTemplate.queryForList(sql, args.toArray());
            }
        };
    }

    // ==================== 客户/供应商域 ====================

    /**
     * 客户供应商查询（对应业务域 MD_PARTNER）：敏感字段（电话/地址）不返回，由权限层另行管控
     */
    @Bean("mes_partner_query")
    public MesBaseQueryToolFunction partnerQueryTool(JdbcTemplate jdbcTemplate) {
        return new MesBaseQueryToolFunction(jdbcTemplate, "客户供应商") {
            @Override
            protected List<Map<String, Object>> doQuery(Long tenantId, String keyword, String status, int limit) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("客户列表", jdbcTemplate.queryForList(
                        "SELECT c.code AS 客户编码, c.name AS 客户名称,"
                                + " CASE c.type WHEN 1 THEN '企业客户' WHEN 2 THEN '个人' ELSE '其他' END AS 类型"
                                + " FROM mes_md_client c WHERE c.tenant_id = ? AND c.deleted = 0"
                                + " AND (? IS NULL OR c.code LIKE ? OR c.name LIKE ?) ORDER BY c.id LIMIT ?",
                        tenantId, keyword, keyword != null ? like(keyword) : null, keyword != null ? like(keyword) : null, limit));
                result.put("供应商列表", jdbcTemplate.queryForList(
                        "SELECT v.code AS 供应商编码, v.name AS 供应商名称, IFNULL(v.level,'') AS 等级"
                                + " FROM mes_md_vendor v WHERE v.tenant_id = ? AND v.deleted = 0"
                                + " AND (? IS NULL OR v.code LIKE ? OR v.name LIKE ?) ORDER BY v.id LIMIT ?",
                        tenantId, keyword, keyword != null ? like(keyword) : null, keyword != null ? like(keyword) : null, limit));
                return List.of(result);
            }
        };
    }

    /**
     * 状态中文 → 状态码映射
     *
     * @param status  模型传入的状态（中文）
     * @param mapping 格式：中文:码,中文:码
     * @return 状态码；无法识别返回 null（不过滤）
     */
    private static Integer mapStatus(String status, String mapping) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (String pair : mapping.split(",")) {
            String[] kv = pair.split(":");
            if (kv.length == 2 && kv[0].equals(status)) {
                return Integer.valueOf(kv[1]);
            }
        }
        return null;
    }

}
