#!/usr/bin/env node
/**
 * 合并"电子资质域"全部 SQL 为一个一键初始化包：sql/master-credential-sync.sql
 * 依据：用户要求"把电子资质相关 sql 整理，合为一个 sql"；沿用既有 master-bidding-sync.sql 的口径
 *      （只保留表结构 + 最基础数据；幂等；无 DROP/TRUNCATE）。
 * 业务目标：新环境一条命令即可让电子证照/企业资质/员工资质/生命周期/规则配置/安许风险功能全部可用。
 */
const fs = require('fs');
const path = require('path');
const SQL = path.join(__dirname, '..');
const OUT = path.join(SQL, 'master-credential-sync.sql');

// 按依赖顺序：证书 → 预警 → 验真 → 企业资质 → 合规/要素 → 资质到期 → 员工资质 → 生命周期 → 规则配置 → 安许风险 → 字典种子
const DDL_SOURCES = [
  '2026-09-04-certificate.sql',
  '2026-09-05-cert-alert.sql',
  '2026-09-06-cert-verify-history.sql',
  '2026-09-07-enterprise-qualification.sql',
  '2026-09-09-qualification-compliance.sql',
  '2026-09-10-qual-expiry-alert.sql',
  '2026-09-13-personnel-qualification.sql',
  '2026-09-14-cert-lifecycle.sql',
  '2026-09-16-rule-config-center.sql',
  '2026-09-17-safety-permit-risk.sql',
  '2026-09-19-cert-dict-register.sql',
// 2026-09-20 新增：平台审查任务中心（bidding_review_task + review_task_status/review_biz_type 字典）
// 契约：《后端变更请求-平台审查任务中心-2026-09-20》§2 数据模型
'2026-09-20-review-task-center.sql',
  // 2026-09-20 新增：平台审查中心菜单 60005600/601/602（页面 + 通过/驳回权限点）
  // ⚠️ 该脚本原放在 sql/tools/（工具目录不参与打包）⇒ 本机库有菜单、新装库没有。
  //    已移入 history 并在此登记：菜单是"新装库必须具备"的终态数据，必须并入一键包。
  '2026-09-20-review-center-menu.sql',
  // 2026-09-20 新增：员工域 5 类字典（personnel_status / social_pay_status / gender / edu_level / qual_serial）
  // ⚠️ 新增 history 脚本务必登记到本清单：漏登记时重建会**静默抹掉**该脚本内容（新装库缺功能，
  //    运行中的库看不出来）。sql/tools/check-master-mirror.js 专门守这条不变式。
  '2026-09-20-bidding-dict-register-personnel.sql',
  // 2026-09-22 新增：用工形式字典（employment_type 5 类，前端"员工档案"用工形式列/录入项的枚举来源）
  '2026-09-22-bidding-dict-employment-type.sql',
  // 2026-09-23 新增：追溯/备案/主体/外部公告 状态类字典 7 类 23 行（取值逐条对齐后端枚举）
  '2026-09-23-bidding-dict-trace-and-notice-status.sql',
  // 2026-09-24 新增：关键岗位字典（KeyPostEnum 5 值，安许风险联动页下拉）
  '2026-09-24-bidding-dict-key-post.sql',
  // 2026-09-25 新增：验真结论字典 verify_result 3 行 + verify_channel 补 THIRD_PARTY（验真历史读出口接线）
  '2026-09-25-bidding-dict-verify-result-channel.sql',
  // 2026-09-16 新增：智慧标书审查 7 张业务表（bidding_bid_review_task / rule_set / rule_item / issue /
  //   report / content / ai_log）+ bid_review_status 字典。无菜单/授权/ALTER，整段并入。
  '2026-09-16-bid-review.sql',
  // 2026-09-28 新增：安许风险条目工单表 safety_risk_item + risk_item_status 字典 + 3 个按钮权限点。
  //   脚本内无授权段（授权口径同 60005215 先例：超管默认放行），可整段并入。
  '2026-09-28-safety-risk-item.sql',
];

// 字典种子来源（整段并入）：与建表来源分列，便于包尾校验段按"枚举字典行"单独核对期望值。
//   这些脚本只含 INSERT INTO bidding_dict_data（INSERT IGNORE 或 ON DUPLICATE KEY UPDATE），
//   幂等、无菜单/授权，不会破坏"包内无 ALTER/DROP/TRUNCATE/DELETE"口径。
const DICT_SOURCES = [
  // 2026-09-18 附件类型字典 attachment_type 35 类（bidding 通用附件域，前端附件下拉/图标来源）
  '2026-09-18-bidding-attachment-type-dict.sql',
  // 2026-09-20 证件大类 cert_category + cert_type_category 映射（证照筛选归口）
  '2026-09-20-cert-category-dict.sql',
  // 2026-09-21 变更留痕 cert_change_type / cert_op_source（变更历史读出口，逐条对齐后端枚举）
  '2026-09-21-cert-change-log-dict.sql',
  // 2026-09-21 契约 13 枚举字典 19 类 75 行（qual_* / rule_* / notice_* / crawl_* / material_* / ai_review_*）
  //   ⚠️ 该脚本用 INSERT ... ON DUPLICATE KEY UPDATE（非 INSERT IGNORE），确认幂等后才并入。
  '2026-09-21-dict-contract13.sql',
  // 2026-09-21 字典覆盖率门禁报出的 6 类（合同/资格预审/异议/人员/主体状态）
  '2026-09-21-dict-coverage-gap6.sql',
  // 2026-09-28 招标交易代码集 123 类 626 条（国家/陕西代码表下沉；整改"照抄枚举写进 Java 常量"）
  '2026-09-28-bidding-code-set-dict.sql',
  // 2026-09-28 证书预警 alert_level / alert_type（预警列表档位与类型）
  '2026-09-28-cert-alert-dict.sql',
  // 2026-09-28 国家矿产种类代码 263 条（national_mineral_type）
  '2026-09-28-national-mineral-dict.sql',
  // 2026-09-28 风险规则触发条件类型 risk_rule_cond_type（消除前端 COND_TYPE_FALLBACK 硬编码）
  '2026-09-28-risk-rule-cond-dict.sql',
  // 2026-09-28 陕西矿产种类 263 条 + 储量单位 263 条（shaanxi_mineral_type / shaanxi_amount_unit）
  '2026-09-28-shaanxi-mineral-dict.sql',
];

// 字典种子"投影"来源：脚本里除字典外还含建表/菜单，但那些部分已被后续脚本取代，整段并入会把
//   废弃号段的菜单行重新带回新装库。故只逐条抽取 INSERT INTO bidding_dict_data（保留段内注释）。
const DICT_EXTRACT_SOURCES = [
  // 2026-09-18 所属企业：只有字典 subject_company_type 3 行需要镜像。
  //   其 system_menu 60002300~60002303 已被 2026-09-18-cert-menu-renumber.sql 第 4 步删除
  //   （现由 2026-09-28-credential-three-ledger-ia.sql 的 60006200 / 60006601~603 承载）；
  //   整段并入不仅复活废弃号段，还会把这些行挂到爬虫域目录 60002000「外部公告采集管理」下。
  //   其 ALTER certificate_info（company_id/company_name，一次性增量）在当前"所属企业后端暂不落地"口径下
  //   无需进终态包（列已无 Java DO 引用），故一并排除。
  '2026-09-18-subject-company.sql',
];
// 仅含菜单的脚本（旧号段）不并入：其内容已被 09-18 的"终态菜单段"取代
const MENU_SOURCE = '2026-09-18-cert-menu-renumber.sql';
// 菜单/权限点补充脚本：按 MENU_SOURCE 相同的口径"截到校验段"并入（只取权限点注册，不含授权语句——
// 本包口径是"授权属业务决策，见包尾授权指引"）
const MENU_EXTRA_SOURCES = [
  '2026-09-21-bidding-rule-config-menu.sql',
  // 2026-09-26 新增：企业资质到期预警处理独立权限点（决策④ 岗位职责分离，见待决策清单 ④；
  //   后端 QualificationController#handleAlert 已切换 @PreAuthorize 至 bidding:qualification:alert-handle）
  '2026-09-26-qualification-alert-handle-menu.sql',
  // 2026-09-28 新增：三台账信息架构落地（页面退役转按钮 + 设置区 + 补注册 2 个缺失权限点）
  // 依据：docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md
  //   「证件全生命周期」(60005400) / 「安许风险联动」(60005500) 退役为按钮并改挂对应台账页；
  //   新增 60006000 段「设置」（主体档案 / 所属企业 / 规则配置 / CA 签章台账）。
  // ⚠️ 本脚本含 UPDATE 语句（改 type/parent）；打包器按 MENU_EXTRA 口径"截到授权段"，故 UPDATE 会被保留。
  '2026-09-28-credential-three-ledger-ia.sql',
  // 2026-09-29 权限审计批次（统一脚本）：4 个缺失权限点登记（subject-auth / tender-doc）+
  //   菜单项归位与去重（三台账重构后 6760/6761 让位给 60006100/60006101，条件 UPDATE）。
  //   来源：全库权限审计（代码 @PreAuthorize 有、system_menu 无承载 ⇒ 非超管恒 403）。
  //   号段 60006300~60006303（三台账重构脚本已按「先登记再占用」让位）。
  '2026-09-29-权限审计批次-菜单与授权统一.sql',
  // =====================================================================
  // 2026-10 补齐：以下 14 个 history 脚本此前未登记来源清单，重建一键包会**静默丢失**
  //   （check-master-mirror.js 门槛报出 27 个未镜像文件，本段与上面的 DDT/DICT/DDL 清单共同补齐）。
  //   口径：菜单/权限点注册截到授权段（授权属业务决策，见包尾授权指引），含 UPDATE 的退役/归位语句保留。
  // =====================================================================
  // ⚠️ 2026-09-03 公示公告域菜单（notice）**有意不并入**：其 60001000/60001100 页面行指向的
  //   views/bidding/notice/index.vue 已于 2026-09-16 删除（提交 9938a243f「删除公告/公示管理与
  //   平台授权管理的废弃前端页面」），公告权限点 bidding:notice:* 已由 2026-09-20-perm-carrier-and-tenant-fix.sql
  //   以 type=3 按钮承载。整段并入会把已退役的 type=2 死页面带回新装库（check-menu-pages 立刻报错），
  //   故本包不并入该脚本；check-master-mirror.js 的 SKIP_FILES 已登记该文件与理由。
  // 2026-09-16 智慧标书审查管理端菜单 60007000/60007100~103/60007400~402（含 3 条结构校正 UPDATE）
  //   ⚠️ 该脚本无"② 角色授权"/"校验段"标记，授权段仅以注释"-- 授权："引导；打包器已改为同时按
  //      "首条 INSERT INTO system_role_menu" 截断，故此脚本的授权行不入包（授权属业务决策）。
  '2026-09-16-bid-review-menu.sql',
  // 2026-09-17 标书审查管理端重排：新增页面 60007450 + 归位 UPDATE（截到校验段，授权段随截断丢弃）
  '2026-09-17-bid-review-admin-reorg.sql',
  // 2026-09-20 补登「企业资质修改」权限点 60005216（截到"② 角色授权"）
  '2026-09-20-qualification-update-menu.sql',
  // 2026-09-28 资格审查委员会：建表 bidding_prequal_committee + fund_source 字典 + 权限点 60006221
  //   ⚠️ 该脚本非纯菜单（含 CREATE TABLE/字典 + "④ 角色授权"）。按 MENU_EXTRA 口径截到授权段：
  //      建表/字典/权限点随段保留，授权段（按钮继承页面授权 6771）不入包。这是有意为之，非漏项。
  '2026-09-28-prequal-committee.sql',
  // 2026-09-28 投标保证金权限点 60006209~211（截到"② 角色授权"）
  '2026-09-28-bid-deposit-menu.sql',
  // 2026-09-28 工程合同权限点（挂项目台账 6771，截到"② 角色授权"）
  '2026-09-28-contract-menu.sql',
  // 2026-09-28 投标异议权限点（挂项目台账 6771，截到"② 角色授权"）
  '2026-09-28-objection-menu.sql',
  // 2026-09-28 定标书面报告权限点（挂项目台账 6771，截到"② 角色授权"）
  '2026-09-28-report-menu.sql',
  // 2026-09-28 证件全生命周期「催办」权限点（挂 60005500，无授权段）
  '2026-09-28-cert-lifecycle-urge-menu.sql',
  // 2026-09-28 证照 Excel 导入权限点 bidding:certificate:import（挂 60005100，无授权段）
  '2026-09-28-certificate-import-menu.sql',
  // 2026-09-28 员工证书批量续期权限点 bidding:personnel:cert:renew（挂 60005300，无授权段）
  '2026-09-28-personnel-cert-renew-menu.sql',
  // 2026-09-28 资质导入/导出权限点 bidding:qualification:import/export（挂 60005200，无授权段）
  '2026-09-28-qualification-import-export-menu.sql',
  // 2026-09-28 安许风险条目一键预警权限点 bidding:safety-risk:alert（挂 60005500，无授权段）
  '2026-09-28-safety-risk-alert-menu.sql',
];

const strip = (s) => s.replace(/\r\n/g, '\n').replace(/\s+$/, '') + '\n';
// 源脚本已归档至 sql/history/（合并后不再在根目录维护），故按 history → 根目录顺序解析
const src = (f) => (fs.existsSync(path.join(SQL, 'history', f)) ? path.join(SQL, 'history', f) : path.join(SQL, f));

/**
 * 字典种子投影：从脚本中逐条抽出 INSERT [IGNORE] INTO bidding_dict_data ... ；
 * 扫描到"顶层分号"为止（跳过字符串/反引号/括号），保留段内注释，丢弃其余语句。
 */
function extractDictInserts(text) {
  const out = [];
  const re = /INSERT\s+(?:IGNORE\s+)?INTO\s+`?bidding_dict_data`?/gi;
  let m;
  while ((m = re.exec(text)) !== null) {
    let depth = 0;
    let quote = null;
    let end = -1;
    for (let i = m.index; i < text.length; i++) {
      const c = text[i];
      if (quote) {
        if (c === '\\') { i++; continue; }
        if (c === quote) quote = null;
        continue;
      }
      if (c === "'" || c === '"' || c === '`') { quote = c; continue; }
      if (c === '(') depth++;
      else if (c === ')') depth--;
      else if (c === ';' && depth === 0) { end = i + 1; break; }
    }
    if (end > 0) out.push(text.slice(m.index, end));
  }
  return out.join('\n\n');
}

// 抽取 09-18 的"第 2 步：终态菜单"与"第 5 步：授权指引"
const menuRaw = fs.readFileSync(src(MENU_SOURCE), 'utf8');
function section(startMark, endMark) {
  const i = menuRaw.indexOf(startMark);
  const j = endMark ? menuRaw.indexOf(endMark, i) : menuRaw.length;
  if (i < 0) throw new Error('未找到段标记: ' + startMark);
  return menuRaw.slice(i, j >= 0 ? j : menuRaw.length).replace(/\s+$/, '');
}
const menuSection = section('-- 第 2 步：证照域迁入新号段', '-- 第 3 步：角色授权随迁');
const grantGuidance = section('-- 第 5 步：安许风险联动新页面的授权', '-- ---------------------------------------------------------------------\n-- 校验');

// 复用 master-bidding-sync 的授权行格式
const master = fs.readFileSync(path.join(SQL, 'master-bidding-sync.sql'), 'utf8');
const colsMatch = master.match(/INSERT[^;]*?INTO\s+`?system_role_menu`?\s*\(([^)]+)\)/i);
const grantCols = colsMatch ? colsMatch[1].replace(/\s+/g, ' ') : 'role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id';

const header = [
  '-- =====================================================================',
  '-- 电子资质域 · 一键初始化包（master-credential-sync.sql）',
  '-- =====================================================================',
  '-- 业务库：ruoyi-vue-pro（电子资质全部业务表；本环境 system_menu / system_role_menu 同库）。',
  '--',
  '-- 【用途】新环境一次性建好"电子资质"全部功能：电子证照台账 + 文件版本 + 验真 + 到期预警、企业资质与',
  '--   资质标准/要素/达标核查、员工资质（档案/证书/履历/材料完备性）、证件全生命周期（变更/续期/注销吊销/年检）、',
  '--   规则配置中心与业务字典、安许 A/B/C 风险联动。执行后菜单、权限点、默认阈值、枚举字典齐备。',
  '--',
  '-- 【与 master-bidding-sync.sql 的关系】后者是招投标主流程初始包（不含电子资质域，避免互相覆盖）；',
  '--   本包只补电子资质域，两者可分别独立执行，无先后依赖（本包不引用招投标主流程的表）。',
  '--',
  '-- 【口径】SQL 只保留表结构 + 最基础数据（默认阈值、枚举字典、菜单与权限点）；业务数据不入脚本。',
  '--   全部语句幂等（CREATE TABLE IF NOT EXISTS / INSERT IGNORE / ON DUPLICATE KEY UPDATE），',
  '--   历史 ALTER TABLE（加列/改列）已折叠进对应基表 CREATE TABLE —— 包内无 ALTER，重复执行不会报"列已存在"；',
  '--   包内无 DROP / TRUNCATE / DELETE（历史迁移动作已在本包中"固化终态"，不再需要执行迁移脚本）。',
  '--',
  '-- 【执行】',
  '--   mysql -uroot -p123456 --default-character-set=utf8mb4 ruoyi-vue-pro < sql/master-credential-sync.sql',
  '--   结构自检：node sql/tools/validate-sql-structure.js sql/master-credential-sync.sql',
  '--',
  '-- 【菜单号段】证书与资质台账使用 60005000 段（60005000 目录 / 600051xx 证照台账 / 600052xx 企业资质 /',
  '--   600053xx 员工资质 / 600056xx 平台审查中心）+ 60006000 段（设置）+ 60007000 段（智慧标书审查）。',
  '--   2026-09-28 三台账重组：原「证件全生命周期」(60005400) 与「安许风险联动」(60005500) 已退役为按钮，',
  '--   分别挂到证照台账(60005100)与企业资质(60005200)下；其内容并入对应台账的页签。',
  '--   设计依据：docs/模块设计与规范/电子证照域-三台账信息架构与界面重设计-2026-09-28.md',
  '--   口径：页面退役只改 type/parent，id 与权限码不变 —— system_role_menu 授权行继续有效。',
  '--   原 60002000/600021xx/600022xx/600023xx/600024xx 号段已废弃（与外部公告采集域冲突）；',
  '--   60004000/600042xx 段已被《docs/前端规范-全域接口说明/投标流程跟踪功能设计方案.md》§8.3 预约',
  '--   （投标流程跟踪：父级 60004000 + 页面 60004200 + 按钮 60004201~203），按"先登记再占用"让出，',
  '--   故本域改用 60005000 段（登记时 SQL 侧 60005000~60005999 无任何占用）。',
  '--   本包直接落终态，不产生废弃号段的行。',
  '--',
  '-- 【不包含】角色菜单授权（除超级管理员）——授权属业务决策；见包尾"授权指引"按需执行。',
  '--   也不包含：演示/业务数据、文件实体（file_id 由 FileApi 上传产生）、AI 渠道与密钥。',
  '-- =====================================================================',
  '',
].join('\n');

let parts = [header];
// 已并入的"来源段"正文（含混合脚本截断后的实际内容）：包尾校验段的期望值必须按**实际进包的内容**算，
// 否则期望值会与实际执行条数脱节（历史缺陷：只按 DDL_SOURCES 统计，字典/菜单补充段被漏计）。
const inlined = [];
function pushBlock(file, note, body) {
  parts.push('-- #####################################################################');
  parts.push('-- 【来源】' + file + note);
  parts.push('-- #####################################################################');
  parts.push(body, '');
  inlined.push(body);
}
for (const f of DDL_SOURCES) {
  pushBlock(f, '（已并入本包；原文件已归档至 sql/history/）', strip(fs.readFileSync(src(f), 'utf8')));
}
// 字典种子来源：整段并入
for (const f of DICT_SOURCES) {
  pushBlock(f, '（字典种子；已并入本包）', strip(fs.readFileSync(src(f), 'utf8')));
}
// 字典种子投影来源：只抽 INSERT INTO bidding_dict_data，其余部分已被后续脚本取代
for (const f of DICT_EXTRACT_SOURCES) {
  const body = extractDictInserts(fs.readFileSync(src(f), 'utf8'));
  if (!body) throw new Error('字典投影为空，请检查脚本: ' + f);
  pushBlock(f, '（字典种子投影；仅抽取 bidding_dict_data 段，建表/菜单/ALTER 已被后续脚本取代）', body + '\n');
}
parts.push('-- #####################################################################');
parts.push('-- 【来源】' + MENU_SOURCE + ' 第 2 步（菜单终态段）——原脚本的迁移/恢复动作不再需要');
parts.push('-- #####################################################################');
parts.push(menuSection, '');
inlined.push(menuSection);
for (const f of MENU_EXTRA_SOURCES) {
  const raw = fs.readFileSync(src(f), 'utf8');
  // 截到授权段之前：包内只落权限点注册/菜单终态，授权语句留给 history 脚本与包尾授权指引。
  // 三类标记取最早出现者：① 显式"② 角色授权"节标题；② "校验段"标题；③ 首条 system_role_menu 授权 INSERT
  //   （2026-09-16-bid-review-menu.sql 无 ①② 标记，仅有"-- 授权："注释，故必须有 ③ 兜底）。
  const cutGrant = raw.indexOf('-- ② 角色授权');
  const cutCheck = raw.indexOf('-- ========== 校验 ==========');
  const cutRoleMenu = raw.search(/INSERT\s+(?:IGNORE\s+)?INTO\s+`?system_role_menu`?/i);
  const end = [cutGrant, cutCheck, cutRoleMenu].filter((x) => x >= 0).sort((a, b) => a - b)[0];
  const body = (end >= 0 ? raw.slice(0, end) : raw)
    .replace(/^SET NAMES[^\n]*\n/m, '')
    .replace(/\s+$/, '');
  pushBlock(f, '（菜单与权限点补充；已并入本包；授权段已按口径截除）', body);
}
parts.push('-- =====================================================================');
parts.push('-- 菜单与权限点已就位。以下为"授权指引"（默认不执行）：');
parts.push('--   1) 超级管理员（role_id=1，租户 0）默认放行全部权限，无需授权；');
parts.push('--   2) 其他角色需按需授权，示例见下（给"已拥有企业资质菜单"的角色补齐电子资质全量菜单）。');
parts.push('-- =====================================================================');
parts.push(grantGuidance, '');
parts.push('-- ---------------------------------------------------------------------');
parts.push('-- 校验段：执行后应打印本包的规模（与包内实际条数一致即正常）');
parts.push('-- ---------------------------------------------------------------------');
parts.push("SELECT '电子资质域建表' AS item, COUNT(*) AS cnt FROM information_schema.TABLES");
// 期望值统一由"实际进包的全部来源段"推导（DDL + 字典 + 菜单终态段 + 菜单补充 + 字典投影），
// 避免只统计 DDL_SOURCES 导致字典/菜单补充段的期望值偏小、与实际执行条数脱节。
// 去注释：注释里出现 "CREATE TABLE IF NOT EXISTS，" 这类文字时，正则会回溯并误捕成表名 "IF"
//   （2026-10 修复：期望表数一度多出 1 张）。故统计一律在去注释文本上进行。
const noComment = (s) => s.split('\n').filter((l) => !l.trim().startsWith('--')).join('\n');
const allText = noComment(inlined.join('\n'));
const created = [...allText.matchAll(/CREATE\s+TABLE(\s+IF\s+NOT\s+EXISTS)?\s+\`?(\w+)/gi)].map(m => m[2])
  .filter((v, i, a) => a.indexOf(v) === i).sort();
// 精确统计某条 INSERT 的顶层元组数（跳过字符串字面量），使"期望值"与校验查询口径一致
function countTuplesIn(text, i) {
  // 从 VALUES 之后开始数，避免把 INSERT 的"列清单 (...) 也算成一个元组"
  const tail = text.slice(i);
  const vm = tail.match(/\bVALUES\b/i);
  if (vm) i = i + vm.index + vm[0].length;
  let depth = 0, n = 0, started = false;
  for (let k = i; k < text.length; k++) {
    const c = text[k];
    if (c === "'") { k++; while (k < text.length && !(text[k] === "'" && text[k - 1] !== '\\')) k++; continue; }
    if (c === '(') { depth++; if (depth === 1) started = true; }
    else if (c === ')') { depth--; if (depth === 0 && started) { n++; started = false; } }
    else if (c === ';' && depth === 0) break;
  }
  return n;
}
// 同一张表可能分多条 INSERT（按 group 分批），必须全部累加
function countTuples(text, anchorRe) {
  const re = new RegExp(anchorRe.source, anchorRe.flags.includes('g') ? anchorRe.flags : anchorRe.flags + 'g');
  let total = 0, m;
  while ((m = re.exec(text)) !== null) total += countTuplesIn(text, m.index);
  return total;
}
const dictRows = countTuples(allText, /INSERT[^;]*?INTO\s+`?bidding_dict_data`?/i);
const cfgRows = countTuples(allText, /INSERT[^;]*?INTO\s+`?bidding_rule_config`?\s*\(/i);
const menuRows = countTuples(allText, /INSERT[^;]*?INTO\s+`?system_menu`?/i);
parts.push("  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + created.map(t => "'" + t + "'").join(',') + ")");
parts.push("-- 期望：" + created.length + " 张表（包内 CREATE TABLE 数）");
// 菜单号段：本包 60005（证照/资质/审查中心/设置）+ 60006（三台账补充）+ 60007（标书审查），
//   故校验范围取 60005000~60007999（旧范围 60004xxx 只覆盖证照域，会把新增菜单漏出校验）。
parts.push("UNION ALL SELECT '菜单与权限点(60005~60007 段)', COUNT(*) FROM system_menu WHERE id BETWEEN 60005000 AND 60007999 AND deleted = b'0'");
parts.push("-- 期望：" + menuRows + " 行（目录 + 页面 + 按钮权限点；覆盖 60005/60006/60007 全部号段）");
parts.push("UNION ALL SELECT '默认规则阈值', COUNT(*) FROM bidding_rule_config WHERE deleted = b'0'");
parts.push("-- 期望：" + cfgRows + " 行（证照阈值 + 安许风险参数；重复执行时已存在行走 ON DUPLICATE KEY UPDATE，不新增）");
parts.push("UNION ALL SELECT '枚举字典行', COUNT(*) FROM bidding_dict_data WHERE deleted = b'0';");
parts.push("-- 期望：" + dictRows + " 行（证照/资质/人员/合同/公告/代码表等全部枚举字典；含 ON DUPLICATE KEY UPDATE 类型的幂等种子）");
parts.push('');

// ---------------------------------------------------------------------
// ALTER → 基表折叠：MySQL 8 没有 ADD COLUMN IF NOT EXISTS，ALTER 会让"可重复执行"失效。
// 一键包应当是"终态结构"，故把 ALTER 的 ADD/MODIFY 列并进对应 CREATE TABLE，并删掉 ALTER 语句本身。
// ---------------------------------------------------------------------
function splitTop(s) {
  const out = []; let depth = 0, cur = '';
  for (let i = 0; i < s.length; i++) {
    const c = s[i];
    if (c === "'" || c === '"' || c === '\`') { const q = c; cur += c; i++;
      while (i < s.length && !(s[i] === q && s[i - 1] !== '\\')) { cur += s[i]; i++; }
      if (i < s.length) cur += s[i]; continue; }
    if (c === '(') depth++;
    if (c === ')') depth--;
    if (c === ',' && depth === 0) { out.push(cur); cur = ''; continue; }
    cur += c;
  }
  if (cur.trim()) out.push(cur);
  return out;
}
function matchParen(s, open) {
  let depth = 0;
  for (let i = open; i < s.length; i++) {
    const c = s[i];
    if (c === "'" || c === '"' || c === '\`') { const q = c; i++;
      while (i < s.length && !(s[i] === q && s[i - 1] !== '\\')) i++; continue; }
    if (c === '(') depth++;
    else if (c === ')') { depth--; if (depth === 0) return i; }
  }
  return -1;
}
/** 去掉列定义末尾的定位子句（AFTER x / FIRST）——折进 CREATE TABLE 时它不合法。 */
function stripPosition(def) {
    return def.replace(/\s+AFTER\s+`?\w+`?\s*$/i, '').replace(/\s+FIRST\s*$/i, '');
}

/**
 * 保证每张表的 id 是 AUTO_INCREMENT（2026-09-13；曾被并发覆盖，已有独立自检防回退）。
 * 事故：应用侧不自生成主键（与平台一致），域表 id 无 AUTO_INCREMENT ⇒ 一切写操作失败
 * （java.sql.SQLException: Field 'id' doesn't have a default value）。
 */
function ensureAutoIncrement(text) {
    return text.replace(/`id`\s+bigint\s+NOT NULL(?!\s+AUTO_INCREMENT)/gi, '`id` bigint NOT NULL AUTO_INCREMENT');
}

function foldAlters(text) {
  const rec = new Map(); const removable = [];
  const re = /ALTER\s+TABLE\s+\`?(\w+)\`?\s+([\s\S]*?);/gi;
  let m;
  while ((m = re.exec(text)) !== null) {
    const table = m[1];
    const r = rec.get(table) || { adds: [], mods: new Map(), other: [] };
    for (const raw of splitTop(m[2])) {
      const c = raw.trim();
      const add = c.match(/^ADD\s+COLUMN\s+([\s\S]+)$/i);
      // 定位子句（AFTER/FIRST）只在 ALTER 里合法，折进 CREATE 会报 near 'AFTER ...'
      if (add) { r.adds.push(stripPosition(add[1].trim())); continue; }
      const mod = c.match(/^MODIFY\s+COLUMN\s+\`?(\w+)\`?\s+([\s\S]+)$/i);
      if (mod) { r.mods.set(mod[1], stripPosition(mod[2].trim())); continue; }
      r.other.push(c);
    }
    rec.set(table, r);
    if (r.other.length === 0) removable.push({ table, sql: m[0] });
  }
  if (rec.size === 0) return text;
  let out = text;
  for (const [table, r] of rec) {
    const head = new RegExp('CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+\`?' + table + '\`?\\s*\\(', 'i');
    const hm = head.exec(out);
    if (!hm) { console.log('  [fold] 未找到基表，跳过: ' + table); continue; }
    const open = hm.index + hm[0].length - 1;
    const close = matchParen(out, open);
    let body = out.slice(open + 1, close);
    for (const [col, def] of r.mods) {
      // 2026-09-13 修复（干跑实测；曾被并发覆盖，已做成独立自检 check-package-invariants.js 防回退）：
      // 替换整行时必须**保留行尾逗号**，否则 CREATE TABLE 语法错误（实测第 184 行中断）。
      const lineRe = new RegExp('(^|\\n)(\\s*)`' + col + '`[^\\n]*?(,?)\\s*(?=\\n|$)', 'i');
      if (lineRe.test(body)) body = body.replace(lineRe, (s, p1, p2, p3) => p1 + p2 + '`' + col + '` ' + def + (p3 || ''));
      else console.log('  [fold] MODIFY 目标列不存在，改为新增: ' + table + '.' + col);
    }
    const newCols = r.adds.filter(a => {
      const nm = (a.match(/^\`?(\w+)\`?/) || [])[1];
      if (!nm) return false;
      const exists = new RegExp('(^|\\n)\\s*\`' + nm + '\`\\s', 'i').test(body);
      if (exists) console.log('  [fold] 列已存在，跳过: ' + table + '.' + nm);
      return !exists;
    });
    if (r.mods.size && !newCols.length) { /* 仅 MODIFY */ }
    if (newCols.length) {
      body = body.replace(/\s*$/, '');
      if (!/,$/.test(body)) body += ',';
      body += '\n' + newCols.map(a => '    ' + a).join(',\n') + '\n';
    }
    out = out.slice(0, open + 1) + body + out.slice(close);
  }
  for (const it of removable) out = out.replace(it.sql, '-- [已折叠进基表 CREATE TABLE] 原 ALTER TABLE \`' + it.table + '\` 语句在此移除（保证包可重复执行）');
  return out;
}

let finalText = parts.join('\n');
finalText = foldAlters(finalText);
finalText = ensureAutoIncrement(finalText);
// 菜单号段终态：让出 60004 段（流程跟踪方案已预约），统一落到 60005 段
const beforeCount = (finalText.match(/60004\d{3}/g) || []).length;
finalText = finalText.replace(/60004(\d{3})/g, '60005$1');
console.log('— 菜单号段：60004xxx → 60005xxx，改写 ' + beforeCount + ' 处');
fs.writeFileSync(OUT, finalText, 'utf8');
// 构建后立刻跑独立不变式自检：谁把上述修复覆盖掉，都会在这里当场失败（不再静默产出坏包）
try {
    require('child_process').execFileSync(process.execPath, [require('path').join(__dirname, 'check-package-invariants.js')], { stdio: 'inherit' });
} catch (e) {
    console.error('一键包不变式自检未通过，已中止构建。');
    process.exit(1);
}
console.log('已生成 ' + OUT + ' (' + (fs.statSync(OUT).size / 1024).toFixed(1) + ' KB)');
console.log('— 合并来源：建表 ' + DDL_SOURCES.length + ' 个 + 字典 ' + DICT_SOURCES.length
  + ' 个 + 字典投影 ' + DICT_EXTRACT_SOURCES.length + ' 个 + 菜单终态段 1 个 + 菜单补充 ' + MENU_EXTRA_SOURCES.length + ' 个');
