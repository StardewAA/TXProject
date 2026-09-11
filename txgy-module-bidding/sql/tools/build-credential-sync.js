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
];
// 仅含菜单的脚本（旧号段）不并入：其内容已被 09-18 的"终态菜单段"取代
const MENU_SOURCE = '2026-09-18-cert-menu-renumber.sql';

const strip = (s) => s.replace(/\r\n/g, '\n').replace(/\s+$/, '') + '\n';
// 源脚本已归档至 sql/history/（合并后不再在根目录维护），故按 history → 根目录顺序解析
const src = (f) => (fs.existsSync(path.join(SQL, 'history', f)) ? path.join(SQL, 'history', f) : path.join(SQL, f));

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
  '--   600053xx 员工资质 / 600054xx 证件全生命周期 / 600055xx 安许风险联动）。',
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
for (const f of DDL_SOURCES) {
  const body = strip(fs.readFileSync(src(f), 'utf8'));
  parts.push('-- #####################################################################');
  parts.push('-- 【来源】' + f + '（已并入本包；原文件已归档至 sql/history/）');
  parts.push('-- #####################################################################');
  parts.push(body, '');
}
parts.push('-- #####################################################################');
parts.push('-- 【来源】' + MENU_SOURCE + ' 第 2 步（菜单终态段）——原脚本的迁移/恢复动作不再需要');
parts.push('-- #####################################################################');
parts.push(menuSection, '');
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
// 期望表清单由包内实际 CREATE TABLE 推导，避免"校验段与实际建表脱节"
const noComment = (s) => s.split('\n').filter(l => !l.trim().startsWith('--')).join('\n');
const ddlText = DDL_SOURCES.map(f => noComment(fs.readFileSync(src(f), 'utf8'))).join('\n');
const created = [...ddlText.matchAll(/CREATE\s+TABLE(\s+IF\s+NOT\s+EXISTS)?\s+\`?(\w+)/gi)].map(m => m[2])
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
const dictRows = countTuples(ddlText, /INSERT[^;]*?INTO\s+`?bidding_dict_data`?/i);
const cfgRows = countTuples(ddlText, /INSERT[^;]*?INTO\s+`?bidding_rule_config`?\s*\(/i);
const menuRows = countTuples(menuSection, /INSERT[^;]*?INTO\s+`?system_menu`?/i);
parts.push("  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN (" + created.map(t => "'" + t + "'").join(',') + ")");
parts.push("-- 期望：" + created.length + " 张表（包内 CREATE TABLE 数）");
parts.push("UNION ALL SELECT '菜单与权限点(60004xxx)', COUNT(*) FROM system_menu WHERE id BETWEEN 60004000 AND 60004599 AND deleted = b'0'");
parts.push("-- 期望：" + menuRows + " 行（目录 + 页面 + 按钮权限点）");
parts.push("UNION ALL SELECT '默认规则阈值', COUNT(*) FROM bidding_rule_config WHERE deleted = b'0'");
parts.push("-- 期望：" + cfgRows + " 行（证照阈值 + 安许风险参数；重复执行时已存在行走 ON DUPLICATE KEY UPDATE，不新增）");
parts.push("UNION ALL SELECT '枚举字典行', COUNT(*) FROM bidding_dict_data WHERE deleted = b'0';");
parts.push("-- 期望：" + dictRows + " 行（cert_type / holder_type / review_result / material_category / material_type / safety_risk_flag / person_risk_flag / risk_level）");
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
      if (add) { r.adds.push(add[1].trim()); continue; }
      const mod = c.match(/^MODIFY\s+COLUMN\s+\`?(\w+)\`?\s+([\s\S]+)$/i);
      if (mod) { r.mods.set(mod[1], mod[2].trim()); continue; }
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
      const lineRe = new RegExp('(^|\\n)(\\s*)\`' + col + '\`[^\\n]*', 'i');
      if (lineRe.test(body)) body = body.replace(lineRe, (s, p1, p2) => p1 + p2 + '\`' + col + '\` ' + def);
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
// 菜单号段终态：让出 60004 段（流程跟踪方案已预约），统一落到 60005 段
const beforeCount = (finalText.match(/60004\d{3}/g) || []).length;
finalText = finalText.replace(/60004(\d{3})/g, '60005$1');
console.log('— 菜单号段：60004xxx → 60005xxx，改写 ' + beforeCount + ' 处');
fs.writeFileSync(OUT, finalText, 'utf8');
console.log('已生成 ' + OUT + ' (' + (fs.statSync(OUT).size / 1024).toFixed(1) + ' KB)');
console.log('— 合并来源 ' + DDL_SOURCES.length + ' 个 + 菜单终态段 1 个');
