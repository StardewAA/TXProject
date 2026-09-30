# bookmark-dict.json 生成报告

- 生成时间：2026-09-22T07:23:57.203Z
- 字典：投标标书_变量字典.md（1334 个唯一变量名，另有 59 处共享变量重复出现）
- 模板：投标标书_主模板.docx（1334 个唯一书签）
- 基础变量（去 _N/_appendix 变体）：1315 个
- 形态：scalar 99 个 / table-cell 1235 个
- 数据来源：TENDER 23，FACT 46，MANUAL 10，DERIVE 16，PRICING 1062，STAFF 177

## 章节分布

| 章节 | 变量数 |
| --- | ---: |
| 封面 | 7 |
| 一、投标函及投标函附录 | 48 |
| 二、法定代表人身份证明 | 8 |
| 三、授权委托书 | 6 |
| 五、投标报价计价表 | 1 |
| 2、工程项目总造价表 | 42 |
| 3、单项工程造价汇总表 | 46 |
| 4、单位工程造价汇总表 | 16 |
| 5、分部分项工程量清单计价表 | 82 |
| 6、措施项目费清单计价表 | 122 |
| 7、其它项目清单计价表 | 122 |
| 8、计日工计价表 | 54 |
| 9、总承包服务费计价表 | 109 |
| 10、规费、税金项目清单计价表 | 47 |
| 11、工程量清单综合单价分析表 | 260 |
| 12、主要材料价格表 | 161 |
| 七、项目管理机构 | 177 |
| 八、资格审查资料 | 26 |

## 差集（字典 vs 模板书签）

- 模板有、字典没有（0 个）：无
- 字典有、模板没有（0 个）：无

## 无标准字段映射的标量书签（19 个）

这些书签没有与事实卡/招标解析标准字段建立映射：MANUAL 手工项、GENERATE 生成项、STAFF 人员台账（P3 通道）、PRICING 报价数据（P2 通道），或 date/established 拆分与大写金额等 DERIVE 派生变量（由 buildBookmarkValues 特殊处理，无需映射）：

| 书签 | 含义 | 数据来源 |
| --- | --- | --- |
| `date_year` | 投标日期（年） | DERIVE|TENDER |
| `date_month` | 投标日期（月） | DERIVE|TENDER |
| `date_day` | 投标日期（日） | DERIVE|TENDER |
| `appendix_extra_1_no` | 补充条款1序号（模板示例「5」，回填时整段替换） | MANUAL |
| `appendix_extra_1_content` | 补充条款1内容 | MANUAL |
| `appendix_extra_2_no` | 补充条款2序号（模板示例「6」，回填时整段替换） | MANUAL |
| `appendix_extra_2_content` | 补充条款2内容 | MANUAL |
| `established_year` | 成立时间（年） | DERIVE|FACT |
| `established_month` | 成立时间（月） | DERIVE|FACT |
| `established_day` | 成立时间（日） | DERIVE|FACT |
| `specialty` | 专业（表头「专业：」后的空位，如 土建 / 安装） | TENDER|PRICING |
| `person_name` | 姓名 | STAFF |
| `person_age` | 年龄 | STAFF |
| `person_education` | 学历 | STAFF |
| `person_title` | 职称 | STAFF |
| `person_position` | 职务 | STAFF |
| `person_contract_position` | 拟在本合同任职 | STAFF |
| `person_graduation_school_major` | 毕业院校与专业（整段示例，回填时整段替换） | STAFF |
| `company_established_date` | 成立时间 | DERIVE|FACT |
