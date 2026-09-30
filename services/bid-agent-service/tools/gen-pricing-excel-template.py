# -*- coding: utf-8 -*-
"""
生成《投标标书》报价数据标准 Excel 模板（P2 计价表数据通道 · 标准导入格式）。

表头与列结构与 services/bid-agent-service/templates/bookmark-dict.json 推导的映射一致，
是 Java 侧（BidPricingServiceImpl）解析与 Node 侧（pricingMapping.ts）书签展开的共同契约：
  - 首非空行为表头，其下为数据行（空行跳过）；
  - shared 表：变量名 / 取值 两列，键为书签基础名（bid_total / measure_fee / duration_days / …）；
  - bill/measure/other/service/analysis/material/project_cost/unit_cost：按行序映射模板数据行；
  - daywork：组别列取值 人工/材料/机械，分流到 labor / daywork_material / machine 三组；
  - fee：费用项为固定 11 项清单，只填数值列；
  - cost_breakdown：前 5 行固定费用项，其后最多 10 个备用行。
示例行仅作格式参考，上传前请替换或删除；说明页不会被解析（未知表名自动跳过）。

用法：python tools/gen-pricing-excel-template.py [输出.xlsx]
"""
import sys
from pathlib import Path

from openpyxl import Workbook
from openpyxl.styles import Alignment, Border, Font, PatternFill, Side

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(
    r"E:\txgycloud-master\txgy-module-bidding\docs\开发联调与运维\投标标书_报价数据标准模板.xlsx")

FONT_NAME = "微软雅黑"
HEADER_FILL = PatternFill("solid", fgColor="D9E1F2")
SAMPLE_FILL = PatternFill("solid", fgColor="FFF2CC")
FIXED_FILL = PatternFill("solid", fgColor="E2EFDA")
HEADER_FONT = Font(name=FONT_NAME, size=10, bold=True)
DATA_FONT = Font(name=FONT_NAME, size=10)
SAMPLE_FONT = Font(name=FONT_NAME, size=10, color="7F7F7F", italic=True)
THIN = Side(style="thin", color="BFBFBF")
BORDER = Border(left=THIN, right=THIN, top=THIN, bottom=THIN)
CENTER = Alignment(horizontal="center", vertical="center")
LEFT = Alignment(horizontal="left", vertical="center", wrap_text=True)


def write_sheet(ws, headers, rows, widths=None, sample_rows=(), fixed_rows=()):
    """写表头 + 数据行；sample_rows 为示例行号集合（1 起），fixed_rows 为固定行号集合"""
    for col, header in enumerate(headers, start=1):
        cell = ws.cell(row=1, column=col, value=header)
        cell.font = HEADER_FONT
        cell.fill = HEADER_FILL
        cell.border = BORDER
        cell.alignment = CENTER
    if widths:
        for col, width in enumerate(widths, start=1):
            ws.column_dimensions[chr(64 + col)].width = width
    ws.freeze_panes = "A2"
    for row_no, row in enumerate(rows, start=2):
        for col, value in enumerate(row, start=1):
            cell = ws.cell(row=row_no, column=col, value=value)
            cell.font = SAMPLE_FONT if row_no in sample_rows else DATA_FONT
            cell.border = BORDER
            cell.alignment = CENTER if col <= 2 else LEFT
            if row_no in sample_rows:
                cell.fill = SAMPLE_FILL
            elif row_no in fixed_rows:
                cell.fill = FIXED_FILL


wb = Workbook()

# ---------- 说明页（未知表名，解析时自动跳过） ----------
legend = wb.active
legend.title = "填写说明"
notes = [
    ["投标标书报价数据标准模板 · 填写说明"],
    [""],
    ["1. 每个工作表的第一行是表头（列名固定，不要改动），从第二行开始填数据；空行自动跳过。"],
    ["2. 黄色底纹的行是「示例行」，只用于演示格式，上传前请替换为真实数据或整行删除。"],
    ["3. 绿色底纹的行是固定费用项（fee 表 11 项、cost_breakdown 前 5 项），名称不要改，只填/替换数值列。"],
    ["4. shared 表填写共享变量：投标总价、措施项目费、安全文明施工措施费、工期、质量标准、专业、编制日期。"],
    ["   键名与取值示例见表内；bidder_name/project_name 等由企业资料与招标解析自动回填，不需要在此填写。"],
    ["5. daywork 表用「组别」列区分人工/材料/机械，每组按行序回填到计日工表对应分组。"],
    ["6. 行数说明：模板文档每张表有固定行数（分部分项 11 行、措施/其它 20 行、服务 18 行、分析 20 行、材料 23 行等）。"],
    ["   数据超出模板行数时，系统会截断并在导出时告警；如需更多行，请用「行扩展模板」（bid-agent 侧 tools/expand-template-rows.mjs）。"],
    ["7. 上传入口：后台「知识库管理 → 资料管理」→ 新增材料，材料角色选「PRICING（报价数据）」，并选择所属项目。"],
    ["8. 本模板由 tools/gen-pricing-excel-template.py 生成，列结构与 bookmark-dict.json 字典映射保持一致。"],
]
for row in notes:
    legend.append(row)
legend["A1"].font = Font(name=FONT_NAME, size=12, bold=True)
for row in legend.iter_rows(min_row=3, max_row=legend.max_row, max_col=1):
    for cell in row:
        cell.font = Font(name=FONT_NAME, size=10)
legend.column_dimensions["A"].width = 110

# ---------- shared：共享变量 ----------
ws = wb.create_sheet("shared")
write_sheet(ws, ["变量", "取值"], [
    ["bid_total", "12865000.00"],
    ["measure_fee", "234567.89"],
    ["safety_civil_fee", "23456.78"],
    ["duration_days", "120"],
    ["quality_standard", "合格"],
    ["specialty", "土建"],
    ["date_year", "2026"],
    ["date_month", "9"],
    ["date_day", "22"],
], widths=[22, 20], sample_rows={2, 3, 4, 5, 6, 7, 8, 9, 10})

# ---------- 通用计价表 ----------
ws = wb.create_sheet("bill")
write_sheet(ws, ["序号", "项目编号", "项目名称", "计量单位", "工程数量", "综合单价", "合价"], [
    ["1", "010101001001", "挖一般土方（示例行，请替换）", "m³", "100", "12.50", "1250.00"],
    ["2", "010101001002", "回填土方（示例行，请替换）", "m³", "50", "8.00", "400.00"],
], widths=[8, 16, 30, 10, 12, 12, 14], sample_rows={2, 3})

for name in ["measure", "other"]:
    ws = wb.create_sheet(name)
    write_sheet(ws, ["序号", "项目名称", "计量单位", "工程数量", "综合单价", "合价"], [
        ["1", "示例项目（示例行，请替换）", "项", "1", "1000.00", "1000.00"],
    ], widths=[8, 30, 10, 12, 12, 14], sample_rows={2})

ws = wb.create_sheet("daywork")
write_sheet(ws, ["组别", "项目名称", "单位", "暂定数量", "综合单价", "合价"], [
    ["人工", "普工（示例行，请替换）", "工日", "10", "200.00", "2000.00"],
    ["材料", "水泥 P.O42.5（示例行，请替换）", "t", "2", "500.00", "1000.00"],
    ["机械", "挖掘机（示例行，请替换）", "台班", "1", "1500.00", "1500.00"],
], widths=[10, 30, 10, 12, 12, 14], sample_rows={2, 3, 4})

ws = wb.create_sheet("service")
write_sheet(ws, ["序号", "项目名称", "计量单位", "工程数量", "综合单价", "合价"], [
    ["1", "发包人发包专业工程管理服务费", "项", "1", "1000.00", "1000.00"],
    ["2", "发包人供应材料、设备保管费", "项", "1", "500.00", "500.00"],
    ["3", "示例服务费（示例行，请替换）", "项", "1", "3000.00", "3000.00"],
], widths=[8, 30, 10, 12, 12, 14], sample_rows={4}, fixed_rows={2, 3})

ws = wb.create_sheet("fee")
write_sheet(ws, ["费用项", "计量单位", "工程数量", "综合单价", "合价"], [
    ["养老保险", "项", "1", "100.00", "100.00"],
    ["失业保险", "项", "1", "20.00", "20.00"],
    ["医疗保险", "项", "1", "80.00", "80.00"],
    ["工伤保险", "项", "1", "15.00", "15.00"],
    ["残疾人就业保险", "项", "1", "10.00", "10.00"],
    ["女工生育保险", "项", "1", "8.00", "8.00"],
    ["住房公积金", "项", "1", "120.00", "120.00"],
    ["危险作业意外伤害保险", "项", "1", "5.00", "5.00"],
    ["营业税", "项", "1", "300.00", "300.00"],
    ["城市维护建设税", "项", "1", "21.00", "21.00"],
    ["教育附加费", "项", "1", "9.00", "9.00"],
], widths=[22, 10, 12, 12, 14], fixed_rows=set(range(2, 13)))

ws = wb.create_sheet("analysis")
write_sheet(ws, ["序号", "项目编码", "项目名称", "单位", "工程内容", "组价依据",
                 "人工费", "材料费", "机械费", "管理费", "利润", "风险", "合计"], [
    ["1", "010101001001", "挖一般土方（示例行，请替换）", "m³", "土方开挖", "定额计价",
     "5.00", "1.00", "2.00", "1.50", "1.00", "2.00", "12.50"],
], widths=[6, 16, 26, 8, 14, 12, 10, 10, 10, 10, 10, 10, 10], sample_rows={2})

ws = wb.create_sheet("material")
write_sheet(ws, ["序号", "材料编码", "材料名称、规格、型号", "单位", "数量", "单价", "备注"], [
    ["1", "M001", "水泥 P.O42.5（示例行，请替换）", "t", "2", "500.00", "示例备注"],
], widths=[8, 12, 32, 8, 10, 10, 14], sample_rows={2})

for name in ["project_cost", "unit_cost"]:
    ws = wb.create_sheet(name)
    write_sheet(ws, ["序号", "单项工程名称", "造价"], [
        ["1", "土建工程（示例行，请替换）", "12865000.00"],
    ], widths=[8, 30, 16], sample_rows={2})

ws = wb.create_sheet("cost_breakdown")
rows = [
    ["分部分项工程费", "10000000.00"],
    ["措施项目费", "234567.89"],
    ["其他项目费", "500.00"],
    ["规费", "100000.00"],
    ["税金", "900000.00"],
]
rows += [["", ""] for _ in range(10)]  # 备用行 6-15
write_sheet(ws, ["费用项", "金额（元）"], rows, widths=[26, 16], fixed_rows={2, 3, 4, 5, 6})

wb.save(OUT)
print(f"已生成：{OUT}")
