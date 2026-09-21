package cn.iocoder.txgy.module.report.service.dataset;

import lombok.Getter;

import java.util.List;

/**
 * 报表白名单目录（「简单模式」专用）
 *
 * 工作人员不写 SQL：从这里选表、勾字段，SQL 由服务端自动生成。
 * 目录是刻意收窄的白名单——只放业务上安全、结构稳定的表，不在目录里的表不能建报表。
 *
 * @author OPENLAB BS
 */
public final class ReportTableCatalog {

    /**
     * 目录列定义
     *
     * @param field  字段名（数据库列名）
     * @param label  中文显示名（自动作为报表表头）
     * @param isDate 是否时间列（勾选后可作为日期筛选条件）
     */
    public record ColumnDef(String field, String label, boolean isDate) {
    }

    /**
     * 目录表定义
     *
     * @param table   表名
     * @param label   中文表名
     * @param columns 可选列
     */
    public record TableDef(String table, String label, List<ColumnDef> columns) {
    }

    private static final List<TableDef> TABLES = List.of(
            new TableDef("mes_pro_feedback", "生产报工记录", List.of(
                    new ColumnDef("code", "报工单号", false),
                    new ColumnDef("work_order_id", "工单编号", false),
                    new ColumnDef("process_id", "工序编号", false),
                    new ColumnDef("workstation_id", "工位编号", false),
                    new ColumnDef("item_id", "物料编号", false),
                    new ColumnDef("feedback_quantity", "报工数量", false),
                    new ColumnDef("qualified_quantity", "合格数量", false),
                    new ColumnDef("unqualified_quantity", "不良数量", false),
                    new ColumnDef("labor_scrap_quantity", "工废数量", false),
                    new ColumnDef("material_scrap_quantity", "料废数量", false),
                    new ColumnDef("feedback_time", "报工时间", true))),
            new TableDef("mes_pro_work_order", "生产工单", List.of(
                    new ColumnDef("code", "工单号", false),
                    new ColumnDef("name", "工单名称", false),
                    new ColumnDef("product_id", "产品编号", false),
                    new ColumnDef("quantity", "计划数量", false),
                    new ColumnDef("quantity_produced", "已生产数量", false),
                    new ColumnDef("quantity_scheduled", "排产数量", false),
                    new ColumnDef("status", "状态", false),
                    new ColumnDef("request_date", "计划日期", true),
                    new ColumnDef("finish_date", "完工日期", true))),
            new TableDef("mes_wm_material_stock", "实时库存", List.of(
                    new ColumnDef("item_id", "物料编号", false),
                    new ColumnDef("warehouse_id", "仓库编号", false),
                    new ColumnDef("batch_code", "批次号", false),
                    new ColumnDef("quantity", "库存数量", false),
                    new ColumnDef("update_time", "更新时间", true))),
            new TableDef("mes_dv_machinery", "设备台账", List.of(
                    new ColumnDef("code", "设备编码", false),
                    new ColumnDef("name", "设备名称", false),
                    new ColumnDef("machinery_type_id", "设备类型编号", false),
                    new ColumnDef("status", "状态", false),
                    new ColumnDef("last_check_time", "最近点检时间", true),
                    new ColumnDef("last_mainten_time", "最近保养时间", true))),
            new TableDef("mes_dv_repair", "设备维修记录", List.of(
                    new ColumnDef("code", "维修单号", false),
                    new ColumnDef("name", "维修名称", false),
                    new ColumnDef("machinery_id", "设备编号", false),
                    new ColumnDef("require_date", "要求完成时间", true),
                    new ColumnDef("finish_date", "实际完成时间", true),
                    new ColumnDef("status", "状态", false))),
            new TableDef("mes_md_item", "物料档案", List.of(
                    new ColumnDef("code", "物料编码", false),
                    new ColumnDef("name", "物料名称", false),
                    new ColumnDef("specification", "规格型号", false))));

    private ReportTableCatalog() {
    }

    public static List<TableDef> list() {
        return TABLES;
    }

    public static TableDef get(String table) {
        return TABLES.stream().filter(def -> def.table().equals(table)).findFirst().orElse(null);
    }

}
