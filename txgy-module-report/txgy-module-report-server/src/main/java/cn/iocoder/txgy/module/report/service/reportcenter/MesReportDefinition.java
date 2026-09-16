package cn.iocoder.txgy.module.report.service.reportcenter;

import lombok.Getter;

import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.report.enums.ErrorCodeConstants.REPORT_CENTER_NOT_OPEN;

/**
 * MES 报表中心 - 报表定义注册表
 *
 * 面向业务人员（车间、品质、生产文员）的报表清单：只暴露「报表名称 + 中文表头 + 导出」，
 * SQL 与数据集细节留在后端，业务人员无需接触。
 *
 * 每个定义对应 report_dataset 中一个已登记的数据集（受控取数：租户隔离、行数上限、超时、审计）。
 *
 * @author OPENLAB BS
 */
@Getter
public enum MesReportDefinition {

    /**
     * 生产日报
     */
    PRODUCTION_DAILY("mes_daily_production", "生产日报", "按日期、工单、工序汇总的报工数量与合格情况",
            List.of(ColumnDef.of("report_date", "日期"),
                    ColumnDef.of("work_order_code", "工单号"),
                    ColumnDef.of("process_name", "工序"),
                    ColumnDef.of("record_count", "报工笔数"),
                    ColumnDef.of("feedback_quantity", "报工数量"),
                    ColumnDef.of("qualified_quantity", "合格数量"),
                    ColumnDef.of("unqualified_quantity", "不合格数量")),
            List.of(ParamDef.of("startDate", "开始日期"), ParamDef.of("endDate", "结束日期"))),

    /**
     * 工单完工报表
     */
    WORK_ORDER_COMPLETION("mes_work_order_completion", "工单完工报表", "工单计划量、已生产量与完成进度",
            List.of(ColumnDef.of("work_order_code", "工单号"),
                    ColumnDef.of("work_order_name", "工单名称"),
                    ColumnDef.of("item_code", "物料编码"),
                    ColumnDef.of("item_name", "产品名称"),
                    ColumnDef.of("plan_quantity", "计划数量"),
                    ColumnDef.of("produced_quantity", "已生产数量"),
                    ColumnDef.of("progress_percent", "完成进度(%)"),
                    ColumnDef.of("status", "工单状态", Map.of(
                            "0", "草稿", "1", "已确认", "2", "已完成", "3", "已取消", "10", "已下发", "11", "生产中")),
                    ColumnDef.of("request_date", "计划日期"),
                    ColumnDef.of("finish_date", "完工日期")),
            List.of(ParamDef.of("startDate", "计划开始日期"), ParamDef.of("endDate", "计划结束日期"))),

    /**
     * 不良品统计
     */
    DEFECT_SUMMARY("mes_defect_summary", "不良品统计", "按物料、工序汇总的不良品与工废料废数量",
            List.of(ColumnDef.of("item_code", "物料编码"),
                    ColumnDef.of("item_name", "物料名称"),
                    ColumnDef.of("process_name", "工序"),
                    ColumnDef.of("unqualified_quantity", "不良数量"),
                    ColumnDef.of("labor_scrap_quantity", "工废数量"),
                    ColumnDef.of("material_scrap_quantity", "料废数量"),
                    ColumnDef.of("other_scrap_quantity", "其他废品数量")),
            List.of(ParamDef.of("startDate", "开始日期"), ParamDef.of("endDate", "结束日期"))),

    /**
     * 物料耗用报表
     */
    MATERIAL_CONSUMPTION("mes_material_consumption", "物料耗用报表", "生产领料消耗：按工单、物料汇总",
            List.of(ColumnDef.of("work_order_code", "工单号"),
                    ColumnDef.of("process_name", "工序"),
                    ColumnDef.of("consume_date", "领料日期"),
                    ColumnDef.of("item_code", "物料编码"),
                    ColumnDef.of("item_name", "物料名称"),
                    ColumnDef.of("consume_quantity", "耗用数量")),
            List.of(ParamDef.of("startDate", "开始日期"), ParamDef.of("endDate", "结束日期"))),

    /**
     * 设备运行与维修统计
     */
    MACHINERY_EFFICIENCY("mes_machinery_efficiency", "设备运行与维修统计", "设备状态、维修次数与维修工时（完整 OEE 待接入设备运行时长）",
            List.of(ColumnDef.of("machinery_code", "设备编码"),
                    ColumnDef.of("machinery_name", "设备名称"),
                    ColumnDef.of("status", "设备状态", Map.of("1", "停机", "2", "生产中", "3", "维护中")),
                    ColumnDef.of("repair_count", "维修次数"),
                    ColumnDef.of("repair_hours", "维修工时(小时)"),
                    ColumnDef.of("last_check_time", "最近点检时间"),
                    ColumnDef.of("last_mainten_time", "最近保养时间")),
            List.of()),

    /**
     * 生产报工明细台账
     */
    FEEDBACK_DETAIL("mes_feedback_detail", "生产报工明细台账", "逐笔报工明细：谁、在哪个工位、报了哪个工序多少数量",
            List.of(ColumnDef.of("feedback_code", "报工单号"),
                    ColumnDef.of("work_order_code", "工单号"),
                    ColumnDef.of("process_name", "工序"),
                    ColumnDef.of("workstation_name", "工位"),
                    ColumnDef.of("item_code", "物料编码"),
                    ColumnDef.of("item_name", "产品名称"),
                    ColumnDef.of("feedback_quantity", "报工数量"),
                    ColumnDef.of("qualified_quantity", "合格数量"),
                    ColumnDef.of("unqualified_quantity", "不合格数量"),
                    ColumnDef.of("feedback_time", "报工时间")),
            List.of(ParamDef.of("startDate", "开始日期"), ParamDef.of("endDate", "结束日期")));

    /**
     * 数据集编码（对应 report_dataset.code）
     */
    private final String code;
    /**
     * 报表名称（业务人员看到的名字）
     */
    private final String name;
    /**
     * 报表用途说明
     */
    private final String description;
    /**
     * 导出列定义（顺序即 Excel 列顺序）
     */
    private final List<ColumnDef> columns;
    /**
     * 可筛选参数
     */
    private final List<ParamDef> params;

    MesReportDefinition(String code, String name, String description,
                        List<ColumnDef> columns, List<ParamDef> params) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.columns = columns;
        this.params = params;
    }

    /**
     * 按数据集编码获取报表定义
     */
    public static MesReportDefinition getByCode(String code) {
        for (MesReportDefinition definition : values()) {
            if (definition.code.equals(code)) {
                return definition;
            }
        }
        throw exception(REPORT_CENTER_NOT_OPEN);
    }

    /**
     * 导出列定义
     *
     * @param field       数据集字段名
     * @param label       Excel 中文表头
     * @param valueLabels 值的中文映射（如状态 1/2/3 -> 停机/生产中/维护中），可为空
     */
    public record ColumnDef(String field, String label, Map<String, String> valueLabels) {

        public static ColumnDef of(String field, String label) {
            return new ColumnDef(field, label, Map.of());
        }

        public static ColumnDef of(String field, String label, Map<String, String> valueLabels) {
            return new ColumnDef(field, label, valueLabels);
        }
    }

    /**
     * 报表筛选参数
     *
     * @param name  参数名（对应数据集声明的参数）
     * @param label 界面显示名
     */
    public record ParamDef(String name, String label) {

        public static ParamDef of(String name, String label) {
            return new ParamDef(name, label);
        }
    }

}
