/**
 * 全量回填冒烟（改进版落地方案 P0 冒烟标准，回归资产）：
 *   A. 字典全量回填：对字典全部基础变量生成确定性取值，回填模板，要求 0 跳过，
 *      产物通过五项审计（见 audit-filled-docx.mjs）；
 *   B. 交付链路验证：走 fillTemplate（标准字段取值 → buildBookmarkValues → 回填 → 废标红线断言），
 *      并验证「六、施工组织设计」章节正文插入点（插入成功、正文落在标题段之后）；
 *   C. 红线断言检测能力自测：构造两处取值不一致的合成 XML，断言必须报出；一致时不报。
 *
 * 用法：node tools/fill-all-bookmarks.mjs [模板.docx] [字典.json]
 * 缺省：templates/投标标书_主模板.docx + templates/bookmark-dict.json
 * 产物：templates/投标标书_全量回填验证.docx、templates/投标标书_全量回填取值.json、templates/投标标书_交付链路验证.docx
 */
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { fillBookmarkTemplate } from '../dist/lib/bookmarkFill.js';
import { assertSharedValueConsistency, listBookmarkNames } from '../dist/lib/bookmarkFill.js';
import { fillTemplate } from '../dist/lib/templateDocx.js';
import { buildPricingBookmarkValues } from '../dist/lib/pricingMapping.js';
import { unzip } from '../dist/lib/zip.js';
import { runAudits } from './audit-filled-docx.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const templatePath = path.resolve(root, process.argv[2] ?? 'templates/投标标书_主模板.docx');
const dictPath = path.resolve(root, process.argv[3] ?? 'templates/bookmark-dict.json');

function baseName(name) {
  return name.replace(/_\d+$/, '').replace(/_appendix$/, '');
}

/** 为每个基础变量生成确定性测试取值（按变量名模式），保证回填形态全部命中 */
function valueFor(base) {
  const rules = [
    [/^(date|established)_year$/, (n) => (n === 'date_year' ? '2026' : '2015')],
    [/^date_month$/, () => '9'],
    [/^established_month$/, () => '3'],
    [/^date_day$/, () => '22'],
    [/^established_day$/, () => '18'],
    [/^full_date$/, () => '2026年9月22日'],
    [/^company_established_date$/, () => '2015年3月18日'],
    [/^bid_total_upper$/, () => '人民币壹仟贰佰叁拾肆万伍仟陆佰柒拾捌元玖角'],
    [/^bid_total$/, () => '12345678.90'],
    [/^bidder_name$/, () => '测试投标人名称有限公司'],
    [/^company_bidder_name$/, () => '测试投标人名称有限公司'],
    [/^project_name$/, () => '测试工程项目'],
    [/^tenderer$/, () => '测试招标人'],
    [/^bid_no$/, () => 'BID-2026-001'],
    [/^section_no$/, () => '第一标段'],
    [/^legal_rep_or_agent$/, () => '张三'],
    [/^(legal_rep_name|agent_name|project_manager_name|company_legal_rep_name)$/, () => '张三'],
    [/^company_technical_leader_name$/, () => '李四'],
    [/^company_contact_name$/, () => '王五'],
    [/^specialty$/, () => '土建'],
    [/^quality_standard$/, () => '合格'],
    [/^(duration_days|authorization_days)$/, () => '120'],
    [/^(performance_bond_wan)$/, () => '50'],
    [/^bid_bond_amount$/, () => '500000'],
    [/^bid_bond_type$/, () => '银行保函'],
    [/^(late_penalty_per_day)$/, () => '500'],
    [/^quality_penalty_max$/, () => '50000'],
    [/^business_term$/, () => '长期'],
    [/^company_type$/, () => '有限责任公司'],
    [/^supplementary_notes$/, () => '无'],
    [/^cost_compiler$/, () => '造价工程师'],
    [/^address$/, () => '测试市测试区测试路1号'],
    [/^company_registered_address$/, () => '测试市测试区测试路1号'],
    [/^(phone|fax|company_phone|company_fax|company_legal_rep_phone|company_technical_leader_phone)$/, () => '010-12345678'],
    [/^postal_code$/, () => '100000'],
    [/^company_postal_code$/, () => '100000'],
    [/^legal_rep_gender$/, () => '男'],
    [/^legal_rep_age$/, () => '50'],
    [/^legal_rep_position$/, () => '总经理'],
    [/^(legal_rep_id|agent_id)$/, () => '110101199001011234'],
    [/^company_website$/, () => 'http://www.test.cn'],
    [/^company_organization_structure$/, () => '职能部门制'],
    [/^(company_legal_rep_title|company_technical_leader_title)$/, () => '高级工程师'],
    [/^company_qualification_level$/, () => '建筑工程施工总承包壹级'],
    [/^company_business_license_no$/, () => '91110000TEST00001X'],
    [/^company_business_scope_remark$/, () => '建筑施工'],
    [/^company_registered_capital$/, () => '5000'],
    [/^company_bank_name$/, () => '中国测试银行'],
    [/^company_bank_account$/, () => '6222020200000000000'],
    [/^company_skilled_worker_count$/, () => '50'],
    [/^company_(project_manager|senior_title|middle_title|junior_title)_count$/, () => '10'],
    [/^appendix_clause_no_\d$/, () => '15.4'],
    [/^appendix_extra_\d_no$/, () => '5'],
    [/^appendix_extra_\d_content$/, () => '补充条款测试内容'],
    [/_(quantity)$/, () => '12.5'],
    [/_(unit_price|total_price|amount|fee|subtotal|total)$/, () => '123456.78'],
    [/^measure_fee$/, () => '234567.89'],
    [/^safety_civil_fee$/, () => '23456.78'],
    [/_(labor|material|machine|management|profit|risk)$/, () => '123456.78'],
    [/_(code)$/, () => '010101001001'],
    [/_(no)$/, () => '1'],
    [/_(unit)$/, () => '项'],
    [/_(name_spec)$/, () => '测试材料 规格X'],
    [/_(name)$/, () => '测试项目名称'],
    [/_(days)$/, () => '120'],
    [/_(phone)$/, () => '010-12345678'],
    [/_(capital)$/, () => '5000'],
    [/_(count)$/, () => '10'],
    [/_(bank_name)$/, () => '中国测试银行'],
    [/_(bank_account|account)$/, () => '6222020200000000000'],
    [/_(website)$/, () => 'http://www.test.cn'],
    [/_(id)$/, () => '110101199001011234'],
    [/^person_work_\d_time$/, () => '2020年1月—2021年1月'],
    [/^person_work_\d_similar_project$/, () => '类似项目测试A'],
    [/^person_work_\d_employer_contact$/, () => '发包人 13800000000'],
    [/^person_work_\d_position$/, () => '项目经理'],
    [/_(remark)$/, () => '备注测试'],
    [/_(content)$/, () => '工程内容测试'],
    [/_(pricing_basis)$/, () => '组价依据测试'],
    [/_(major)$/, () => '专业A'],
    [/_(social_insurance)$/, () => '已缴'],
    [/_(certificate_no)$/, () => '证号2026-001'],
    [/_(title)$/, () => '工程师'],
    [/_(position)$/, () => '项目经理'],
    [/_(education)$/, () => '本科'],
    [/_(age)$/, () => '45'],
    [/_(gender)$/, () => '男'],
    [/_(graduation_school_major)$/, () => '测试大学 土木工程'],
    [/_(contract_position)$/, () => '项目经理'],
  ];
  for (const [pattern, make] of rules) {
    if (pattern.test(base)) return make(base);
  }
  return '测试值';
}

function loadDict() {
  return JSON.parse(fs.readFileSync(dictPath, 'utf8'));
}

/** 变体归并（仅当去后缀后的基础名本身也是字典条目时）：bidder_name_2 → bidder_name；
 * 行号命名（cost_breakdown_item_1、appendix_clause_no_1）保持独立 */
function collapseBase(name, dictNames) {
  const stripped = baseName(name);
  return stripped !== name && dictNames.has(stripped) ? stripped : name;
}

/** 阶段 A：字典全量回填 + 五项审计 */
function phaseA(template) {
  console.log('==== 阶段 A：字典全量回填 + 五项审计 ====');
  const dict = loadDict();
  const dictNames = new Set(dict.bookmarks.map((entry) => entry.name));
  const baseNames = [...new Set(dict.bookmarks.map((entry) => collapseBase(entry.name, dictNames)))];
  const baseValues = {};
  for (const base of baseNames) baseValues[base] = valueFor(base);
  // 展开到全部书签名（含 _N 变体），供回填到位审计逐名检查
  const fullValues = {};
  for (const entry of dict.bookmarks) {
    fullValues[entry.name] = baseValues[collapseBase(entry.name, dictNames)];
  }

  const started = Date.now();
  const result = fillBookmarkTemplate(template, baseValues);
  const elapsed = ((Date.now() - started) / 1000).toFixed(2);

  const outPath = path.join(root, 'templates', '投标标书_全量回填验证.docx');
  fs.writeFileSync(outPath, result.buffer);
  const valuesPath = path.join(root, 'templates', '投标标书_全量回填取值.json');
  fs.writeFileSync(valuesPath, `${JSON.stringify(fullValues, null, 2)}\n`);

  console.log(`基础变量：${baseNames.length} 个；书签区域覆盖：${dict.bookmarks.length} 个`);
  console.log(`回填耗时：${elapsed} 秒；命中：${result.filled.length} 个；跳过：${result.skipped.length} 个`);
  if (result.skipped.length > 0) console.log(`  跳过明细：${result.skipped.join('、')}`);
  console.log(`输出：templates/投标标书_全量回填验证.docx（${result.buffer.byteLength} 字节）`);

  const audits = runAudits(template, result.buffer, fullValues);
  const pass = audits.ok && result.skipped.length === 0;
  console.log(`阶段 A 结论：${pass ? '达标（全量 0 跳过 + 五项审计通过）' : '未达标'}`);
  return pass;
}

/** 阶段 B：交付链路（fillTemplate：标准字段 → 回填 → 红线断言 → 六章节正文插入点） */
function phaseB(template) {
  console.log('==== 阶段 B：交付链路 + 六章节正文插入点 ====');
  const stdValues = new Map([
    ['投标人名称', '测试投标人名称有限公司'],
    ['工程名称', '测试工程项目'],
    ['招标编号', 'BID-2026-001'],
    ['标段号', '第一标段'],
    ['投标报价小写', '12865000.00'],
    ['投标报价大写', '壹仟贰佰捌拾陆万伍仟元整'],
    ['工期天数', '120'],
    ['质量标准', '合格'],
    ['措施项目费', '234567.89'],
    ['安全文明施工措施费', '23456.78'],
    ['法定代表人', '张三'],
    ['委托代理人姓名', '张三'],
    ['委托期限', '30'],
    ['签署日期', '2026年9月22日'],
    ['成立时间', '2015年3月18日'],
    ['地址', '测试市测试区测试路1号'],
    ['电话', '010-12345678'],
    ['传真', '010-87654321'],
    ['邮政编码', '100000'],
    ['单位性质', '有限责任公司'],
    ['经营期限', '长期'],
    ['法定代表人性别', '男'],
    ['法定代表人年龄', '50'],
    ['法定代表人职务', '总经理'],
    ['法定代表人身份证号', '110101197501011234'],
    ['委托代理人身份证号', '110101199001011234'],
    ['注册地址', '测试市测试区测试路1号'],
    ['注册资金', '5000'],
    ['注册资本', '5000'],
    ['联系人', '王五'],
    ['网址', 'http://www.test.cn'],
    ['组织结构', '职能部门制'],
    ['法定代表人技术职称', '高级工程师'],
    ['法定代表人电话', '010-12345678'],
    ['技术负责人姓名', '李四'],
    ['技术负责人技术职称', '高级工程师'],
    ['技术负责人电话', '010-12345678'],
    ['企业资质等级', '建筑工程施工总承包壹级'],
    ['项目经理人数', '10'],
    ['营业执照号', '91110000TEST00001X'],
    ['高级职称人员数', '10'],
    ['中级职称人员数', '20'],
    ['初级职称人员数', '30'],
    ['技工人数', '50'],
    ['开户银行', '中国测试银行'],
    ['账号', '6222020200000000000'],
    ['经营范围备注', '建筑施工'],
    ['误期赔偿限额', '500'],
    ['质量违约金限额', '50000'],
    ['项目经理', '张三'],
    ['补充说明', '无'],
    ['造价编制人', '造价工程师'],
    ['投标保证金金额', '500000'],
    ['投标担保类型', '银行保函'],
    ['履约担保金额', '50'],
  ]);
  const sections = [
    { heading: '六、施工组织设计', markdown: '# 测试施工组织设计\n\n这是冒烟测试插入的施工组织设计正文段落。\n\n## 施工方案\n\n测试方案内容。' },
  ];
  let result;
  try {
    result = fillTemplate(template, { values: stdValues, sections });
  } catch (error) {
    console.log(`阶段 B 失败：fillTemplate 抛出异常 → ${error.message}`);
    return false;
  }
  const outPath = path.join(root, 'templates', '投标标书_交付链路验证.docx');
  fs.writeFileSync(outPath, result.buffer);
  console.log(`回填字段：${result.fields.length} 个；章节正文插入：${result.inserted ? '成功' : '未命中标题'}`);

  // 插入点校验：正文片段必须落在「六、施工组织设计」标题段落之后
  const entries = unzip(result.buffer);
  const doc = entries.find((entry) => entry.name === 'word/document.xml');
  const xml = doc.data.toString('utf8');
  const headingIdx = xml.indexOf('六、施工组织设计');
  const bodyIdx = xml.indexOf('这是冒烟测试插入的施工组织设计正文段落');
  const paraAfterHeading = bodyIdx > headingIdx && xml.indexOf('</w:p>', headingIdx) < bodyIdx;
  console.log(`插入点校验：正文位于标题段之后 → ${paraAfterHeading ? '通过' : '异常'}`);
  const pass = result.inserted && paraAfterHeading && result.fields.length > 0;
  console.log(`阶段 B 结论：${pass ? '达标（交付链路 + 红线断言 + 插入点）' : '未达标'}`);
  return pass;
}

/** 阶段 C：红线断言检测能力自测（合成 XML：一处 100、一处 200 → 必须报出） */
function phaseC() {
  console.log('==== 阶段 C：废标红线断言检测能力 ====');
  const mk = (id, text) =>
    `<w:p><w:bookmarkStart w:id="${id}" w:name="bid_total"/><w:r><w:t>${text}</w:t></w:r><w:bookmarkEnd w:id="${id}"/></w:p>`;
  const uniform = mk(1, '100') + mk(2, '100');
  const divergent = mk(3, '100') + mk(4, '200');
  const uniformIssues = assertSharedValueConsistency(uniform, { bid_total: '100' }, ['bid_total']);
  const divergentIssues = assertSharedValueConsistency(divergent, { bid_total: '100' }, ['bid_total']);
  const pass = uniformIssues.length === 0 && divergentIssues.length === 1 && divergentIssues[0].name === 'bid_total';
  console.log(`一致取值 → 断言通过：${uniformIssues.length === 0 ? '是' : '否（误报）'}`);
  console.log(`不一致取值 → 断言报出：${divergentIssues.length > 0 ? `是（${divergentIssues.length} 处）` : '否（漏报）'}`);
  console.log(`阶段 C 结论：${pass ? '达标' : '未达标'}`);
  return pass;
}

/**
 * 阶段 D：计价表数据通道（COPY_PRICING，P2）——合成报价数据 → 字典映射展开 → 回填 → 校验
 * 验证：通用表行序映射、daywork 组别分流、fee 费用项映射、cost_breakdown 行序、共享变量覆盖、
 * 确定性合计派生、行数超限截断告警（20 行 bill 数据 vs 模板 11 行容量）。
 */
function phaseD(template) {
  console.log('==== 阶段 D：计价表数据通道（COPY_PRICING）====');
  const dict = loadDict();  const pricing = {
    shared: {
      bid_total: '12865000.00', measure_fee: '234567.89', safety_civil_fee: '23456.78',
      duration_days: '120', quality_standard: '合格', specialty: '土建',
    },
    tables: {
      bill: Array.from({ length: 20 }, (_, i) => ({
        序号: String(i + 1),
        项目编号: `010101001${String(i + 1).padStart(3, '0')}`,
        项目名称: `分部分项测试项${i + 1}`,
        计量单位: 'm³',
        工程数量: '100',
        综合单价: '12.50',
        合价: '1250.00',
      })),
      measure: [{ 序号: '1', 项目名称: '安全文明施工', 计量单位: '项', 工程数量: '1', 综合单价: '1000.00', 合价: '1000.00' }],
      other: [{ 序号: '1', 项目名称: '暂列金额', 计量单位: '项', 工程数量: '1', 综合单价: '500.00', 合价: '500.00' }],
      daywork: [
        { 组别: '人工', 项目名称: '普工', 单位: '工日', 暂定数量: '10', 综合单价: '200.00', 合价: '2000.00' },
        { 组别: '材料', 项目名称: '水泥', 单位: 't', 暂定数量: '2', 综合单价: '500.00', 合价: '1000.00' },
        { 组别: '机械', 项目名称: '挖掘机', 单位: '台班', 暂定数量: '1', 综合单价: '1500.00', 合价: '1500.00' },
      ],
      service: [{ 序号: '1', 项目名称: '总承包服务费', 计量单位: '项', 工程数量: '1', 综合单价: '3000.00', 合价: '3000.00' }],
      fee: [
        { 费用项: '养老保险', 计量单位: '项', 工程数量: '1', 综合单价: '100.00', 合价: '100.00' },
        { 费用项: '教育附加费', 计量单位: '项', 工程数量: '1', 综合单价: '50.00', 合价: '50.00' },
      ],
      analysis: [{
        序号: '1', 项目编码: '010101001001', 项目名称: '挖一般土方', 单位: 'm³', 工程内容: '土方开挖',
        组价依据: '定额计价', 人工费: '5.00', 材料费: '1.00', 机械费: '2.00', 管理费: '1.50',
        利润: '1.00', 风险: '2.00', 合计: '12.50',
      }],
      material: [{ 序号: '1', 材料编码: 'M001', '材料名称、规格、型号': '水泥 P.O42.5', 单位: 't', 数量: '2', 单价: '500.00', 备注: '测试' }],
      project_cost: [{ 序号: '1', 单项工程名称: '土建工程', 造价: '12865000.00' }],
      unit_cost: [{ 序号: '1', 单项工程名称: '土建工程', 造价: '12865000.00' }],
      cost_breakdown: [
        { 费用项: '分部分项工程费', '金额（元）': '10000000.00' },
        { 费用项: '措施项目费', '金额（元）': '234567.89' },
        { 费用项: '其他项目费', '金额（元）': '500.00' },
        { 费用项: '规费', '金额（元）': '100000.00' },
        { 费用项: '税金', '金额（元）': '900000.00' },
      ],
    },
  };

  const templateNames = new Set(listBookmarkNames(template));
  const { values: pricingValues, truncated } = buildPricingBookmarkValues(dict, pricing, templateNames);

  // 关键映射断言
  const checks = [
    ['通用表行序', pricingValues.bill_2_code === '010101001001' && pricingValues.bill_2_name === '分部分项测试项1'],
    ['超行数据不进模板', pricingValues.bill_13_code === undefined && truncated.length === 1 && truncated[0].table === 'bill' && truncated[0].expected === 20 && truncated[0].available === 11],
    ['daywork 组别分流', pricingValues.labor_0_name === '普工' && pricingValues.daywork_material_0_name === '水泥' && pricingValues.machine_0_name === '挖掘机'],
    ['daywork 小计/总计派生', pricingValues.labor_subtotal === '2000.00' && pricingValues.daywork_total === '4500.00'],
    ['fee 费用项映射', pricingValues.fee_pension_unit_price === '100.00' && pricingValues.fee_education_unit_price === '50.00'],
    ['fee 合计派生', pricingValues.fee_regulation_total === '100.00' && pricingValues.fee_tax_total === '50.00'],
    ['analysis 13 列映射', pricingValues.analysis_3_code === '010101001001' && pricingValues.analysis_3_total === '12.50'],
    ['material 列映射', pricingValues.material_1_name_spec === '水泥 P.O42.5' && pricingValues.material_1_unit_price === '500.00'],
    ['project_cost/unit_cost 映射', pricingValues.project_cost_1_name === '土建工程' && pricingValues.unit_cost_1_amount === '12865000.00'],
    ['cost_breakdown 行序+合计', pricingValues.cost_breakdown_item_1 === '10000000.00' && pricingValues.cost_breakdown_item_5 === '900000.00' && pricingValues.cost_breakdown_total === '11235067.89'],
    ['共享变量', pricingValues.bid_total === '12865000.00' && pricingValues.specialty === '土建'],
    ['汇总合计派生', pricingValues.measure_total_price === '1000.00' && pricingValues.other_total_price === '500.00' && pricingValues.service_total === '3000.00' && pricingValues.bill_total_price === '13750.00' && pricingValues.unit_cost_total === '12865000.00'],
  ];
  let pass = true;
  for (const [label, ok] of checks) {
    console.log(`  ${ok ? '✓' : '✗'} ${label}`);
    pass = pass && ok;
  }

  // 端到端：fillTemplate（标准字段 + 报价合并 → 回填 → 红线断言）
  const stdValues = new Map([
    ['投标人名称', '测试投标人名称有限公司'], ['工程名称', '测试工程项目'], ['签署日期', '2026年9月22日'],
  ]);
  const result = fillTemplate(template, { values: stdValues, pricing });
  const outPath = path.join(root, 'templates', '投标标书_报价通道验证.docx');
  fs.writeFileSync(outPath, result.buffer);
  console.log(`  端到端回填：${result.fields.length} 个字段命中；截断报告：${JSON.stringify(result.pricingTruncated ?? [])}`);
  // 回填到位审计（只针对报价通道产生的取值）
  const audits = runAudits(template, result.buffer, pricingValues);
  const coreOk = audits.results.xmlBalanced.ok && audits.results.bookmarkPairs.ok
    && audits.results.tableGeometry.ok && audits.results.fillInPlace.ok && audits.results.fontSizes.ok;
  console.log(`  报价取值回填到位 + 核心审计：${coreOk ? '通过' : '失败'}（5a 占位符残留为部分回填预期现象）`);
  pass = pass && coreOk && result.pricingTruncated?.length === 1;
  console.log(`阶段 D 结论：${pass ? '达标' : '未达标'}`);
  return pass;
}

const template = fs.readFileSync(templatePath);
const results = [phaseA(template), phaseB(template), phaseC(), phaseD(template)];
const allPass = results.every(Boolean);
console.log('');
console.log(`==== 冒烟总结论：${allPass ? '全部达标' : '存在未达标项'} ====`);
process.exit(allPass ? 0 : 1);
