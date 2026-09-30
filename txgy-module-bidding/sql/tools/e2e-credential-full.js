#!/usr/bin/env node
/* eslint-disable no-console */
/**
 * 电子证照域 · 全量端到端测试脚本
 * ---------------------------------------------------------------------------
 * 用法：
 *   node txgy-module-bidding/sql/tools/e2e-credential-full.js            # 默认：清理 → 造数 → 全量调用 → 出报告
 *   node txgy-module-bidding/sql/tools/e2e-credential-full.js --clean    # 只清理历史废数据（打印判据与前后行数）
 *   node txgy-module-bidding/sql/tools/e2e-credential-full.js --seed     # 只造数（真实上传文件 + 各域写入）
 *   node ... --no-clean                                                  # 全跑但跳过清理
 *
 * 口径：
 *   · bidding-server = http://127.0.0.1:48110，上下文前缀必须是 /admin-api，令牌走 ?token=
 *   · 登录走网关 http://127.0.0.1:48080/admin-api/system/auth/login（tenant-id: 1 / admin / admin123）
 *   · 成功失败只看 body.code（HTTP 恒 200）：0 成功 / 401 未登录 / 403 无权限 / 404 地址不存在
 *   · 写接口多带 @Idempotent → 每次写之间 sleep 1.3s
 *   · 绝不 kill/重启 Java 进程；只做 HTTP + 只读/受限 SQL（删除范围见 cleanUp 判据）
 *
 * 产物：
 *   · 覆盖矩阵 + 失败归因报告 → txgy-module-bidding/docs/电子证照前前端契约/e2e-电子证照域全量-<YYYY-MM-DD>.md
 *   · 造数 id 快照           → txgy-module-bidding/sql/tools/.e2e-credential-state.json
 */
'use strict';

const { execFileSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

/* ============================ 0. 配置 ============================ */

const GW = process.env.E2E_GW || 'http://127.0.0.1:48080';
const BB = process.env.E2E_BB || 'http://127.0.0.1:48110';
const TENANT = '1';
const ENT_ID = Number(process.env.E2E_ENT_ID || 182143816674785);
const HOLDER = '西安鸥鹏互联科技有限公司';
const WRITE_GAP_MS = 1300;

const ROOT = path.resolve(__dirname, '..', '..'); // txgy-module-bidding
const REPORT_DIR = path.join(ROOT, 'docs', '电子证照前前端契约');
const STATE_FILE = path.join(__dirname, '.e2e-credential-state.json');
const JOURNAL_FILE = path.join(__dirname, '.e2e-credential-clean-journal.json');

const argv = process.argv.slice(2);
const ONLY_CLEAN = argv.includes('--clean');
const ONLY_SEED = argv.includes('--seed');
const SKIP_CLEAN = argv.includes('--no-clean');

let TOKEN = process.env.E2E_TOKEN || '';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const today = () => new Date().toISOString().slice(0, 10);
const nowStr = () => new Date().toISOString().replace('T', ' ').slice(0, 19);
const d = (offsetDays) => new Date(Date.now() + offsetDays * 86400000).toISOString().slice(0, 10);

/* ============================ 1. MySQL（只读统计 + 受限删除） ============================ */

function mysql(sql) {
  const out = execFileSync(
    'docker',
    ['exec', '-i', 'txgy-mysql', 'mysql', '-uroot', '-p123456', 'ruoyi-vue-pro', '-N', '-B', '-e', sql],
    { encoding: 'utf8', maxBuffer: 128 * 1024 * 1024, stdio: ['ignore', 'pipe', 'ignore'] },
  );
  return out
    .split(/\r?\n/)
    .filter((l) => l && !l.includes('Using a password'))
    .map((l) => l.trim());
}
function mcount(table, where) {
  const rows = mysql('SELECT COUNT(*) FROM ' + table + (where ? ' WHERE ' + where : ''));
  return Number(rows[0] || 0);
}
/** 中文一律转 HEX 参与 SQL 比较，规避 mysql -e 的中文乱码 */
function hexLike(s) {
  return '%' + Buffer.from(s, 'utf8').toString('hex').toUpperCase() + '%';
}
function dec(hex) {
  return hex === null || hex === undefined || hex === '' ? '' : Buffer.from(hex, 'hex').toString('utf8');
}

/* ============================ 2. HTTP ============================ */

async function login() {
  const res = await fetch(GW + '/admin-api/system/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'tenant-id': TENANT },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  });
  const body = await res.json();
  if (body.code !== 0) throw new Error('登录失败（网关 ' + GW + '）：' + JSON.stringify(body));
  TOKEN = body.data.accessToken;
  return body.data;
}

function buildUrl(p, query) {
  const u = new URL(BB + '/admin-api' + p);
  u.searchParams.set('token', TOKEN);
  if (query) {
    for (const k of Object.keys(query)) {
      const v = query[k];
      if (v === undefined || v === null) continue;
      u.searchParams.set(k, String(v));
    }
  }
  return u;
}

async function rawCall(method, p, opts) {
  opts = opts || {};
  const u = buildUrl(p, opts.query);
  const headers = Object.assign({ 'tenant-id': TENANT }, opts.headers || {});
  const init = { method, headers };
  if (opts.form) {
    init.body = opts.form; // multipart；不要设置 Content-Type，交给 fetch 生成 boundary
  } else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    init.body = JSON.stringify(opts.body);
  }
  const res = await fetch(u, init);
  const text = await res.text();
  const contentType = res.headers.get('content-type') || '';
  let json = null;
  try {
    json = JSON.parse(text);
  } catch {
    json = null;
  }
  return { status: res.status, text, json, contentType };
}

/* ============================ 3. 结果收集 ============================ */

const R = []; // 覆盖率矩阵行
const CLEAN = { before: {}, after: {}, actions: [], detail: [] };

function sentFields(opts) {
  const out = [];
  if (opts.query) out.push(...Object.keys(opts.query).map((k) => k + '(query)'));
  if (opts.body) out.push(...Object.keys(opts.body));
  if (opts.formFields) out.push(...opts.formFields.map((k) => k + '(multipart)'));
  return out;
}

async function T(group, name, method, p, opts, note) {
  opts = opts || {};
  if (method !== 'GET') await sleep(WRITE_GAP_MS);
  let r;
  try {
    r = await rawCall(method, p, opts);
  } catch (e) {
    r = { status: 0, text: 'NETWORK_ERROR: ' + e.message, json: null };
  }
  const code = r.json && typeof r.json.code === 'number' ? r.json.code : null;
  let msg = r.json && r.json.msg !== undefined && r.json.msg !== null ? String(r.json.msg) : String(r.text || '').slice(0, 200);
  let ok = code === 0;
  let extraNote = '';
  if (!ok && r.status === 200 && r.json === null) {
    // 文件流读出口（下载/导出）：不是 CommonResult，200 + 非 JSON 即为正常
    ok = true;
    extraNote = '二进制/文件流响应（非 CommonResult，读出口正常，content-type=' + (r.contentType || '') + '）';
    msg = '[文件流] ' + msg.slice(0, 80);
  }
  if (!ok && r.json && r.json.success === true && Number(r.json.code) === 200) {
    // 官方 CA 互认端点：以 {code:200,success:true} 表达成功
    ok = true;
    extraNote = '官方互认端点：code=200 + success=true（不套 CommonResult）';
  }
  const row = {
    group,
    name,
    method,
    path: p,
    sent: sentFields(opts),
    status: r.status,
    code,
    msg,
    ok,
    raw: String(r.text || '').slice(0, 900),
    note: [note, extraNote].filter(Boolean).join('；'),
    data: r.json ? r.json.data : null,
  };
  R.push(row);
  console.log('[' + (row.ok ? ' OK ' : 'FAIL') + '] ' + group + ' | ' + method + ' ' + p + ' | code=' + code + ' | ' + msg.slice(0, 160));
  return row.data;
}

/* ============================ 4. 真实文件生成 ============================ */

function crc32(buf) {
  let c;
  const table = [];
  for (let n = 0; n < 256; n++) {
    c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  let crc = 0xffffffff;
  for (let i = 0; i < buf.length; i++) crc = table[(crc ^ buf[i]) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}
function pngChunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const typeBuf = Buffer.from(type, 'latin1');
  const crcBuf = Buffer.alloc(4);
  crcBuf.writeUInt32BE(crc32(Buffer.concat([typeBuf, data])), 0);
  return Buffer.concat([len, typeBuf, data, crcBuf]);
}
/** 现场生成合法 PNG（IHDR/IDAT/IEND，真 zlib 压缩） */
function makePng(w, h, seedRgb) {
  const stride = w * 3 + 1;
  const raw = Buffer.alloc(stride * h);
  for (let y = 0; y < h; y++) {
    raw[y * stride] = 0;
    for (let x = 0; x < w; x++) {
      const o = y * stride + 1 + x * 3;
      raw[o] = (seedRgb[0] + x * 3 + y) % 256;
      raw[o + 1] = (seedRgb[1] + y * 5 + x * 2) % 256;
      raw[o + 2] = (seedRgb[2] + x + y * 7) % 256;
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // truecolor
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr),
    pngChunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    pngChunk('IEND', Buffer.alloc(0)),
  ]);
}
/** 现场生成最小合法 PDF（含 xref/trailer，偏移量按 latin1 字节数精确计算） */
function makePdf(title, lines) {
  const esc = (s) =>
    String(s)
      .replace(/[^\x20-\x7e]/g, '?')
      .replace(/\\/g, '\\\\')
      .replace(/\(/g, '\\(')
      .replace(/\)/g, '\\)');
  let content = 'BT /F1 16 Tf 60 790 Td (' + esc(title) + ') Tj ET\n';
  let y = 760;
  for (const l of lines) {
    content += 'BT /F1 10 Tf 60 ' + y + ' Td (' + esc(l) + ') Tj ET\n';
    y -= 18;
  }
  const objs = {
    1: '<< /Type /Catalog /Pages 2 0 R >>',
    2: '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    3: '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>',
    4: '<< /Length ' + Buffer.byteLength(content, 'latin1') + ' >>\nstream\n' + content + 'endstream',
    5: '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  };
  let pdf = '%PDF-1.4\n';
  const offsets = {};
  for (let i = 1; i <= 5; i++) {
    offsets[i] = Buffer.byteLength(pdf, 'latin1');
    pdf += i + ' 0 obj\n' + objs[i] + '\nendobj\n';
  }
  const xref = Buffer.byteLength(pdf, 'latin1');
  pdf += 'xref\n0 6\n0000000000 65535 f \n';
  for (let i = 1; i <= 5; i++) pdf += String(offsets[i]).padStart(10, '0') + ' 00000 n \n';
  pdf += 'trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF\n';
  return Buffer.from(pdf, 'latin1');
}
function makeCsv(header, rows) {
  const q = (v) => '"' + String(v).replace(/"/g, '""') + '"';
  return Buffer.from([header, ...rows].map((r) => r.map(q).join(',')).join('\n') + '\n', 'utf8');
}

/* ============================ 5. 文件上传（真传） ============================ */

async function uploadFile(name, buf, mime, directory) {
  const fd = new FormData();
  fd.append('file', new Blob([buf], { type: mime }), name);
  if (directory) fd.append('directory', directory);
  const data = await T('§10 文件', '上传文件 ' + name, 'POST', '/bidding/file/upload', {
    form: fd,
    formFields: directory ? ['file', 'directory'] : ['file'],
  });
  if (data && data.fileId !== undefined && data.fileId !== null) return data;
  return null;
}

/* ============================ 6. 清理历史废数据 ============================ */

const CLEAN_TABLES = [
  'certificate_info',
  'cert_file_versions',
  'cert_lifecycle_record',
  'cert_verify_history',
  'cert_alert_records',
  'cert_change_logs',
  'enterprise_qualification',
  'qualification_apply_record',
  'qualification_safety_permit',
  'personnel_certificate',
  'bidding_ca_seal',
  'bidding_file',
  'bidding_personnel_profile',
];

function snapshotCounts() {
  const o = {};
  for (const t of CLEAN_TABLES) o[t] = mcount(t);
  return o;
}

function delByIds(table, where, reason) {
  const ids = mysql('SELECT id FROM ' + table + ' WHERE ' + where).filter(Boolean);
  if (ids.length) {
    // 分批删除，避免 IN 列表过长
    for (let i = 0; i < ids.length; i += 500) {
      const chunk = ids.slice(i, i + 500);
      mysql('DELETE FROM ' + table + ' WHERE id IN (' + chunk.join(',') + ')');
    }
  }
  const action = { table, reason, where, ids, count: ids.length };
  CLEAN.actions.push(action);
  console.log('[CLEAN] ' + table + ' 删除 ' + ids.length + ' 行 :: ' + reason);
  return action;
}

function cleanUp() {
  console.log('\n================ 6. 清理历史废数据 ================');
  CLEAN.before = snapshotCounts();
  console.log('清理前行数：' + JSON.stringify(CLEAN.before));

  const TEST_NO = "'XXXX|REVIEW|APPROVE|CAS|DEMO|E2E|TEST'";

  /* --- C1 孤儿子表：外键指向已不存在的证照台账行 --- */
  delByIds('cert_change_logs', 'cert_id NOT IN (SELECT id FROM certificate_info)', '孤儿：cert_id 在 certificate_info 中不存在');
  delByIds('cert_file_versions', 'cert_id NOT IN (SELECT id FROM certificate_info)', '孤儿：cert_id 在 certificate_info 中不存在');
  delByIds('cert_verify_history', 'cert_id NOT IN (SELECT id FROM certificate_info)', '孤儿：cert_id 在 certificate_info 中不存在');
  delByIds('cert_alert_records', 'cert_id IS NOT NULL AND cert_id NOT IN (SELECT id FROM certificate_info)', '孤儿：cert_id 在 certificate_info 中不存在');
  delByIds('cert_lifecycle_record', "biz_domain = 'CERT' AND biz_id NOT IN (SELECT id FROM certificate_info)", '孤儿：CERT 域 biz_id 在 certificate_info 中不存在');
  /* 孤儿：证照文件版本挂的 file_id 在 bidding_file 里查不到 */
  delByIds('cert_file_versions', 'file_id IS NOT NULL AND file_id NOT IN (SELECT file_id FROM bidding_file)', '孤儿：file_id 在 bidding_file 中不存在');

  /* --- C2 certificate_info 坏行 / 测试残留 --- */
  const certWhere = [
    "(cert_name IS NULL OR TRIM(cert_name) = '' OR cert_no IS NULL OR TRIM(cert_no) = '' OR holder_name IS NULL OR TRIM(holder_name) = '')",
    "(HEX(cert_name) LIKE '" + hexLike('示例') + "' OR HEX(cert_name) LIKE '" + hexLike('测试') + "' OR UPPER(cert_name) LIKE '%E2E%' OR UPPER(cert_name) LIKE '%TEST%')",
    "UPPER(cert_no) REGEXP " + TEST_NO,
    "HEX(holder_name) LIKE '" + hexLike('端到端测试') + "'",
    "UPPER(COALESCE(remark,'')) LIKE '%E2E%'",
  ].join(' OR ');
  const certRows = mysql(
    'SELECT id, COALESCE(HEX(cert_no),""), COALESCE(HEX(cert_name),""), COALESCE(HEX(holder_name),""), create_time FROM certificate_info WHERE ' + certWhere,
  );
  const certIds = certRows.map((l) => l.split('\t')[0]).filter(Boolean);
  CLEAN.detail.push({
    title: 'certificate_info 判定为坏行/测试残留的明细',
    head: 'id | certNo | certName | holderName | createTime',
    rows: certRows.map((l) => {
      const c = l.split('\t');
      return [c[0], dec(c[1]), dec(c[2]), dec(c[3]), c[4]].join(' | ');
    }),
  });
  if (certIds.length) {
    for (const child of ['cert_change_logs', 'cert_file_versions', 'cert_verify_history', 'cert_alert_records']) {
      delByIds(child, 'cert_id IN (' + certIds.join(',') + ')', '级联：所属证照台账行被判为废数据');
    }
    delByIds('cert_lifecycle_record', "biz_domain = 'CERT' AND biz_id IN (" + certIds.join(',') + ')', '级联：所属证照台账行被判为废数据');
    delByIds('certificate_info', 'id IN (' + certIds.join(',') + ')', '判据见上（未实现/占位/测试前缀/必填缺失）');
  }

  /* --- C3 企业资质：关键字段缺失的占位行 / 合成证号 --- */
  const qualWhere =
    "(valid_to IS NULL AND (issuing_authority IS NULL OR TRIM(issuing_authority) = '')) OR UPPER(cert_no) REGEXP " +
    TEST_NO +
    " OR UPPER(COALESCE(remark,'')) LIKE '%E2E%'";
  delByIds('enterprise_qualification', qualWhere, '必填缺失（valid_to/issuing_authority 为空）或 certNo 为测试合成串');
  delByIds('qualification_apply_record', 'qual_id NOT IN (SELECT id FROM enterprise_qualification)', '孤儿：qual_id 在 enterprise_qualification 中不存在');
  delByIds('qualification_staff_config', 'qual_id IS NOT NULL AND qual_id NOT IN (SELECT id FROM enterprise_qualification)', '孤儿：qual_id 在 enterprise_qualification 中不存在');
  delByIds('qualification_performance', 'qual_id IS NOT NULL AND qual_id NOT IN (SELECT id FROM enterprise_qualification)', '孤儿：qual_id 在 enterprise_qualification 中不存在');
  delByIds('qualification_asset', 'ent_id NOT IN (SELECT ent_id FROM enterprise_qualification)', '孤儿：ent_id 已无任何企业资质关联');
  delByIds('qualification_compliance_review', 'qual_id IS NOT NULL AND qual_id NOT IN (SELECT id FROM enterprise_qualification)', '孤儿：qual_id 在 enterprise_qualification 中不存在');
  delByIds('qualification_standard', "UPPER(std_version) LIKE 'E2E-%'", '测试标准版本号（E2E-*）');

  /* --- C4 安许证 --- */
  delByIds(
    'qualification_safety_permit',
    "(valid_to IS NULL AND (issuing_authority IS NULL OR TRIM(issuing_authority) = '')) OR HEX(COALESCE(issuing_authority,'')) LIKE '" +
      hexLike('测试') +
      "' OR UPPER(permit_no) REGEXP " +
      TEST_NO +
      " OR UPPER(COALESCE(remark,'')) LIKE '%E2E%'",
    '必填缺失或发证机关/证号为测试占位',
  );

  /* --- C5 员工证书 --- */
  delByIds(
    'personnel_certificate',
    "cert_no IS NULL OR TRIM(cert_no) = '' OR cert_name IS NULL OR TRIM(cert_name) = '' OR (file_id IS NOT NULL AND file_id NOT IN (SELECT file_id FROM bidding_file)) OR UPPER(COALESCE(remark,'')) LIKE '%E2E%'",
    '必填缺失 / file_id 在 bidding_file 中不存在 / 测试残留',
  );

  /* --- C6 CA 签章台账 --- */
  delByIds(
    'bidding_ca_seal',
    "LOWER(document_name) LIKE 'test-%' OR HEX(COALESCE(document_name,'')) LIKE '" +
      hexLike('演示') +
      "' OR (file_id IS NOT NULL AND file_id NOT IN (SELECT file_id FROM bidding_file)) OR UPPER(COALESCE(remark,'')) LIKE '%E2E%'",
    'document_name 为 test-*/演示* 的测试签章，或 file_id 已不存在',
  );

  /* --- C7 文件登记表（只删明显测试命名的，保留 GXCZ 等正常件） --- */
  delByIds(
    'bidding_file',
    "LOWER(name) LIKE '%test-write%' OR LOWER(name) LIKE '%test-seal%' OR HEX(COALESCE(name,'')) LIKE '" + hexLike('演示') + "'",
    'name 为 test-write*/test-seal*/演示* 的测试上传件',
  );

  /* --- C8 员工档案 --- */
  delByIds(
    'bidding_personnel_profile',
    "person_name IS NULL OR TRIM(person_name) = '' OR person_no IS NULL OR TRIM(person_no) = '' OR HEX(COALESCE(person_name,'')) LIKE '" +
      hexLike('端到端测试') +
      "' OR UPPER(person_no) LIKE 'TEST%' OR UPPER(COALESCE(remark,'')) LIKE '%E2E%'",
    '必填缺失 / 端到端测试残留',
  );

  /* --- C9 员工履历（孤儿 / 测试残留）与测试授权记录 --- */
  const personOrphan = "(person_id NOT IN (SELECT id FROM bidding_personnel_profile) OR UPPER(COALESCE(remark,'')) LIKE '%E2E%')";
  delByIds('personnel_training_record', personOrphan, '孤儿：person_id 已不存在 / 测试残留');
  delByIds('personnel_social_security', personOrphan, '孤儿：person_id 已不存在 / 测试残留');
  delByIds('personnel_labor_contract', personOrphan, '孤儿：person_id 已不存在 / 测试残留');
  delByIds('bidding_subject_auth', "UPPER(COALESCE(remark,'')) LIKE '%E2E%'", '测试授权记录（remark 含 E2E）');

  CLEAN.after = snapshotCounts();
  console.log('清理后行数：' + JSON.stringify(CLEAN.after));
  console.log('清理合计删除：' + CLEAN.actions.reduce((a, b) => a + b.count, 0) + ' 行');

  /* 清理台账：持久化到 journal，报告里呈现「历史清理归档 + 本轮」，保证可追溯 */
  let journal = [];
  try {
    journal = JSON.parse(fs.readFileSync(JOURNAL_FILE, 'utf8'));
    if (!Array.isArray(journal)) journal = [];
  } catch (e) {
    journal = [];
  }
  journal.push({
    at: nowStr(),
    before: CLEAN.before,
    after: CLEAN.after,
    total: CLEAN.actions.reduce((a, b) => a + b.count, 0),
    actions: CLEAN.actions.map((a) => ({ table: a.table, where: a.where, reason: a.reason, count: a.count, ids: a.ids })),
    detail: CLEAN.detail,
  });
  fs.writeFileSync(JOURNAL_FILE, JSON.stringify(journal, null, 2), 'utf8');
  return CLEAN;
}

function loadJournal() {
  try {
    const j = JSON.parse(fs.readFileSync(JOURNAL_FILE, 'utf8'));
    return Array.isArray(j) ? j : [];
  } catch (e) {
    return [];
  }
}


/* ============================ 7. 造数 ============================ */

const CTX = {
  files: {},
  certIds: [],
  fileVersionIds: [],
  qualIds: [],
  applyIds: [],
  standardIds: [],
  staffIds: [],
  perfIds: [],
  assetIds: [],
  reviewIds: [],
  permitIds: [],
  lifecycleIds: [],
  personIds: [],
  personCertIds: [],
  sealIds: [],
  ruleSaved: false,
  authId: null,
  countsBefore: {},
  countsAfter: {},
};

const SEED_TABLES = CLEAN_TABLES.concat([
  'qualification_standard',
  'qualification_staff_config',
  'qualification_performance',
  'qualification_asset',
  'qualification_compliance_review',
  'personnel_training_record',
  'personnel_social_security',
  'personnel_labor_contract',
  'bidding_rule_config',
  'bidding_subject_auth',
]);
function seedSnapshot() {
  const o = {};
  for (const t of SEED_TABLES) o[t] = mcount(t);
  return o;
}

const CERT_SEEDS = [
  { n: '营业执照（总部）', t: 'ADMIN_LICENSE', cat: 'IDENTITY', lv: 'A', sc: '建设工程施工总承包叁级及以上', ht: 'ORG', hid: ENT_ID, au: '西安市市场监督管理局', auc: 'XA-SAMR', is: '2019-03-12', vf: '2019-03-12', vt: '2099-12-31' },
  { n: '营业执照（分公司）', t: 'ADMIN_LICENSE', cat: 'IDENTITY', lv: 'B', sc: '分公司经营场所备案', ht: 'ORG', hid: ENT_ID, au: '西安市市场监督管理局高新分局', auc: 'XA-SAMR-GX', is: '2021-07-01', vf: '2021-07-01', vt: '2099-12-31' },
  { n: '质量管理体系认证证书 ISO9001', t: 'SYSTEM_CERT', cat: 'QUALIFICATION', lv: 'AAA', sc: '建筑工程施工全过程质量管理', ht: 'ORG', hid: ENT_ID, au: '中质协质量保证中心', auc: 'CQCA', is: '2024-01-15', vf: '2024-01-15', vt: d(20) },
  { n: '环境管理体系认证证书 ISO14001', t: 'SYSTEM_CERT', cat: 'QUALIFICATION', lv: 'AA', sc: '环境管理体系覆盖施工活动', ht: 'ORG', hid: ENT_ID, au: '中环联合认证中心', auc: 'CEC', is: '2023-11-02', vf: '2023-11-02', vt: d(45) },
  { n: '安全生产许可证（旧证）', t: 'SAFETY_PERMIT', cat: 'QUALIFICATION', lv: '一级', sc: '房屋建筑工程施工', ht: 'ORG', hid: ENT_ID, au: '陕西省住房和城乡建设厅', auc: 'SN-JST', is: '2021-06-01', vf: '2021-06-01', vt: d(-30) },
  { n: '一级建造师注册证书', t: 'PERSON_QUAL', cat: 'QUALIFICATION', lv: 'A', sc: '建筑工程专业一级注册建造师', ht: 'PERSON', hid: ENT_ID, au: '住房和城乡建设部', auc: 'MOHURD', is: '2022-05-18', vf: '2022-05-18', vt: d(10) },
  { n: '2024年度财务审计报告', t: 'AUDIT_REPORT', cat: 'CREDIT', lv: '无', sc: '近三年财务报表审计结论', ht: 'ORG', hid: ENT_ID, au: '天健会计师事务所', auc: 'TJ-CPA', is: '2025-04-08', vf: '2025-04-08', vt: '2099-12-31' },
  { n: '某产业园一期工程业绩证明', t: 'PERFORMANCE', cat: 'PERFORMANCE', lv: '一级', sc: '单项合同额 1.2 亿元总承包业绩', ht: 'ORG', hid: ENT_ID, au: '西安市公共资源交易中心', auc: 'XA-PRTC', is: '2024-09-20', vf: '2024-09-20', vt: '2099-12-31' },
  { n: '近三个月社保证明', t: 'SOCIAL_SECURITY', cat: 'CREDIT', lv: '无', sc: '参保人数 42 人、无欠缴', ht: 'ORG', hid: ENT_ID, au: '西安市社会保险管理中心', auc: 'XA-SI', is: '2026-01-10', vf: '2026-01-10', vt: d(200) },
  { n: '省级文明工地荣誉证书', t: 'HONOR', cat: 'CREDIT', lv: '省级', sc: '陕西省文明工地', ht: 'ORG', hid: ENT_ID, au: '陕西省住房和城乡建设厅', auc: 'SN-JST', is: '2025-12-01', vf: '2025-12-01', vt: d(500) },
  { n: '纳税信用等级证明', t: 'TAX_CREDIT', cat: 'CREDIT', lv: 'A', sc: '连续三年纳税信用 A 级', ht: 'ORG', hid: ENT_ID, au: '国家税务总局西安市税务局', auc: 'XA-TAX', is: '2025-06-30', vf: '2025-06-30', vt: d(365) },
  { n: '法定代表人身份证明', t: 'LEGAL_PERSON_ID', cat: 'IDENTITY', lv: '无', sc: '法定代表人身份核验', ht: 'PERSON', hid: ENT_ID, au: '西安市公安局', auc: 'XA-GA', is: '2018-09-09', vf: '2018-09-09', vt: '2099-12-31' },
  { n: '法人授权委托书', t: 'AUTH_LETTER', cat: 'IDENTITY', lv: '无', sc: '授权办理招投标事务', ht: 'ORG', hid: ENT_ID, au: HOLDER, auc: 'SELF', is: '2026-01-01', vf: '2026-01-01', vt: d(90) },
  { n: '建筑业企业资质证书', t: 'SYSTEM_CERT', cat: 'QUALIFICATION', lv: '一级', sc: '建筑工程施工总承包一级', ht: 'ORG', hid: ENT_ID, au: '陕西省住房和城乡建设厅', auc: 'SN-JST', is: '2020-08-08', vf: '2020-08-08', vt: d(5) },
  { n: '安全生产许可证（新申请）', t: 'SAFETY_PERMIT', cat: 'QUALIFICATION', lv: '二级', sc: '市政公用工程施工', ht: 'ORG', hid: ENT_ID, au: '陕西省住房和城乡建设厅', auc: 'SN-JST', is: '2022-03-03', vf: '2022-03-03', vt: d(-400) },
  { n: '特种设备使用登记证', t: 'SYSTEM_CERT', cat: 'QUALIFICATION', lv: '无', sc: '起重机械使用登记', ht: 'ORG', hid: ENT_ID, au: '西安市市场监督管理局', auc: 'XA-SAMR', is: '2023-02-20', vf: '2023-02-20', vt: d(3) },
];

const QUAL_SEEDS = [
  ['CONSTRUCTION', 'GENERAL_CONTRACTING', 'FIRST', d(-120)],
  ['CONSTRUCTION', 'GENERAL_CONTRACTING', 'SECOND', d(15)],
  ['CONSTRUCTION', 'SPECIAL_CONTRACTING', 'FIRST', d(50)],
  ['CONSTRUCTION', 'LABOR_SUBCONTRACT', 'FIRST', '2099-12-31'],
  ['SURVEY', 'ENGINEERING_SURVEY', 'FIRST', d(240)],
  ['DESIGN', 'ENGINEERING_DESIGN', 'FIRST', d(30)],
  ['SUPERVISION', 'ENGINEERING_SUPERVISION', 'FIRST', d(400)],
  ['TESTING', 'ENGINEERING_TESTING', 'FIRST', d(7)],
  ['CONSTRUCTION', 'GENERAL_CONTRACTING', 'THIRD', d(-60)],
  ['CONSTRUCTION', 'PROFESSIONAL_CONTRACTING', 'SECOND', '2099-12-31'],
  ['DESIGN', 'SPECIAL_DESIGN', 'SECOND', d(95)],
  ['SUPERVISION', 'SPECIAL_SUPERVISION', 'SECOND', d(180)],
];

const PERSON_SEEDS = [
  ['E2E-P-001', '张建国', '610102198501011234', 'MALE', 'BACHELOR', '土木工程', 'REGISTERED_BUILDER'],
  ['E2E-P-002', '李红梅', '610102199003051234', 'FEMALE', 'MASTER', '工程管理', 'TECH_LEAD'],
  ['E2E-P-003', '王振华', '610102198711121234', 'MALE', 'COLLEGE', '建筑工程技术', 'SITE_MGR'],
  ['E2E-P-004', '赵晓峰', '610102199205201234', 'MALE', 'BACHELOR', '安全工程', 'SITE_MGR'],
  ['E2E-P-005', '陈丽君', '610102199408081234', 'FEMALE', 'MASTER', '结构工程', 'TECH_LEAD'],
  ['E2E-P-006', '刘志强', '610102198203151234', 'MALE', 'COLLEGE', '给排水', 'SKILLED_WORKER'],
  ['E2E-P-007', '孙文博', '610102199612251234', 'MALE', 'BACHELOR', '电气工程', 'REGISTERED_BUILDER'],
  ['E2E-P-008', '周雅琴', '610102199109091234', 'FEMALE', 'BACHELOR', '工程造价', 'MID_TITLE'],
  ['E2E-P-009', '吴海涛', '610102198806061234', 'MALE', 'SECONDARY', '测量工程', 'SKILLED_WORKER'],
  ['E2E-P-010', '郑晓丹', '610102199311111234', 'FEMALE', 'DOCTOR', '岩土工程', 'TECH_LEAD'],
];

function requireData(v, what) {
  if (v === null || v === undefined) throw new Error('造数中断：' + what + ' 未返回主键');
  return v;
}

async function seedAll() {
  console.log('\n================ 7. 造数 ================');
  CTX.countsBefore = seedSnapshot();
  const ts = Date.now();

  /* ---------- 7.1 真实文件：现场生成 PDF/PNG/CSV 并逐个真上传 ---------- */
  const pdfOriginal = makePdf('E2E Certificate Original', [
    'holder: ' + HOLDER,
    'uscc: 91350100M000100Y43',
    'generated: ' + nowStr(),
    'tag: E2E credential full-suite seed',
  ]);
  const pdfCopy = makePdf('E2E Certificate Annotated Copy', ['annotation copy for lifecycle evidence', nowStr()]);
  const pngScan = makePng(240, 160, [30, 90, 200]);
  const pdfEvidence = makePdf('E2E Lifecycle Evidence', ['evidence attachment for lifecycle apply', nowStr()]);
  const pdfNewCert = makePdf('E2E Renewed Certificate', ['renewal replacement file', nowStr()]);
  const pdfSealA = makePdf('E2E CA Seal Document A', ['document pending CA seal A', nowStr()]);
  const pngSealB = makePng(320, 200, [200, 60, 40]);
  const pdfPersonCert = makePdf('E2E Personnel Certificate', ['personnel qualification certificate', nowStr()]);
  const pdfTraining = makePdf('E2E Training Record', ['safety training record', nowStr()]);
  const pdfSocial = makePdf('E2E Social Security Evidence', ['social insurance payment evidence', nowStr()]);
  const pdfContract = makePdf('E2E Labor Contract', ['labour contract scan', nowStr()]);
  const pdfPerf = makePdf('E2E Performance Evidence', ['project completion evidence', nowStr()]);
  const csvCert = makeCsv(
    ['certName', 'certNo', 'certType', 'certCategoryCode', 'holderName', 'issuingAuthority', 'validTo'],
    [
      ['E2E-导入-营业执照', 'E2E-IMPORT-CERT-0001', 'ADMIN_LICENSE', 'IDENTITY', HOLDER, '西安市市场监督管理局', '2099-12-31'],
      ['E2E-导入-体系认证', 'E2E-IMPORT-CERT-0002', 'SYSTEM_CERT', 'QUALIFICATION', HOLDER, '中质协质量保证中心', d(120)],
    ],
  );
  const csvQual = makeCsv(
    ['qualSerial', 'qualCategory', 'qualGrade', 'certNo', 'issuingAuthority', 'validTo'],
    [['CONSTRUCTION', 'GENERAL_CONTRACTING', 'FIRST', 'E2E-IMPORT-QUAL-0001', '陕西省住房和城乡建设厅', d(365)]],
  );

  const up = async (key, name, buf, mime, dir) => {
    const r = await uploadFile('E2E-' + ts + '-' + name, buf, mime, dir);
    if (r) CTX.files[key] = r;
    return r;
  };
  await up('pdfOriginal', 'cert-original.pdf', pdfOriginal, 'application/pdf', 'bidding/certificate');
  await up('pdfCopy', 'cert-copy.pdf', pdfCopy, 'application/pdf', 'bidding/certificate');
  await up('pngScan', 'cert-scan.png', pngScan, 'image/png', 'bidding/certificate');
  await up('pdfEvidence', 'lifecycle-evidence.pdf', pdfEvidence, 'application/pdf', 'bidding/cert-lifecycle');
  await up('pdfNewCert', 'lifecycle-new-cert.pdf', pdfNewCert, 'application/pdf', 'bidding/cert-lifecycle');
  await up('pdfSealA', 'ca-seal-a.pdf', pdfSealA, 'application/pdf', 'bidding/seal/original');
  await up('pngSealB', 'ca-seal-b.png', pngSealB, 'image/png', 'bidding/seal/original');
  await up('pdfPersonCert', 'person-cert.pdf', pdfPersonCert, 'application/pdf', 'bidding/personnel');
  await up('pdfTraining', 'training.pdf', pdfTraining, 'application/pdf', 'bidding/personnel');
  await up('pdfSocial', 'social.pdf', pdfSocial, 'application/pdf', 'bidding/personnel');
  await up('pdfContract', 'contract.pdf', pdfContract, 'application/pdf', 'bidding/personnel');
  await up('pdfPerf', 'performance.pdf', pdfPerf, 'application/pdf', 'bidding/qualification');
  await up('csvCert', 'cert-import.csv', csvCert, 'text/csv', 'bidding/import');
  await up('csvQual', 'qual-import.csv', csvQual, 'text/csv', 'bidding/import');

  const fid = (k) => (CTX.files[k] ? CTX.files[k].fileId : undefined);
  if (fid('pdfOriginal') === undefined) throw new Error('造数中断：文件上传未返回 fileId（后端文件域不可用）');

  /* ---------- 7.2 证照台账 16 条 ---------- */
  for (let i = 0; i < CERT_SEEDS.length; i++) {
    const c = CERT_SEEDS[i];
    const body = {
      certName: c.n,
      certNo: 'E2E-CERT-' + String(i + 1).padStart(4, '0'),
      certType: c.t,
      certCategoryCode: c.cat,
      certLevel: c.lv,
      certScope: c.sc,
      holderName: HOLDER,
      holderType: c.ht,
      holderId: c.hid,
      issuingAuthority: c.au,
      issuingAuthorityCode: c.auc,
      issueDate: c.is,
      validFrom: c.vf,
      validTo: c.vt,
      remark: '[E2E] 全量端到端测试数据 第' + (i + 1) + '条',
    };
    const id = await T('§2 证照台账', '创建证照 · ' + c.n, 'POST', '/bidding/certificate/create', { body });
    if (id !== null && id !== undefined) CTX.certIds.push(id);
  }
  if (!CTX.certIds.length) throw new Error('造数中断：证照创建全部失败，后续台账依赖不可用');

  /* 更新一条（全字段 + 乐观锁） */
  const updId = CTX.certIds[0];
  await T('§2 证照台账', '修改证照（全字段+乐观锁）', 'PUT', '/bidding/certificate/update', {
    body: {
      id: updId,
      version: 0,
      certName: CERT_SEEDS[0].n + '（已更新）',
      certNo: 'E2E-CERT-0001',
      certType: CERT_SEEDS[0].t,
      certCategoryCode: CERT_SEEDS[0].cat,
      certLevel: CERT_SEEDS[0].lv,
      certScope: CERT_SEEDS[0].sc + '（更新）',
      holderName: HOLDER,
      holderType: 'ORG',
      holderId: ENT_ID,
      issuingAuthority: CERT_SEEDS[0].au,
      issuingAuthorityCode: CERT_SEEDS[0].auc,
      issueDate: CERT_SEEDS[0].is,
      validFrom: CERT_SEEDS[0].vf,
      validTo: CERT_SEEDS[0].vt,
      remark: '[E2E] 全量端到端测试数据 第1条（已更新）',
    },
  });

  /* 状态流转 4 条 */
  const certVersion = async (id) => {
    const r = await rawCall('GET', '/bidding/certificate/get', { query: { id } });
    return r.json && r.json.data && typeof r.json.data.version === 'number' ? r.json.data.version : 0;
  };
  const statusPlan = [
    [2, 'SUSPENDED'],
    [5, 'REVOKED'],
    [1, 'CANCELLED'],
    [2, 'ACTIVE'],
  ];
  for (const pair of statusPlan) {
    const idx = pair[0];
    const target = pair[1];
    const cid = CTX.certIds[idx];
    if (!cid) continue;
    const ver = await certVersion(cid);
    await T('§2 证照台账', '证照状态流转 → ' + target, 'POST', '/bidding/certificate/change-status', {
      body: { certificateId: cid, targetStatus: target, version: ver, reason: '[E2E] 状态流转验证 → ' + target },
    });
  }

  /* ---------- 7.3 证照文件版本：登记 / 作废 / 更正 ---------- */
  const filePlan = [
    ['ORIGINAL', 'e2e-cert-original.pdf', 'pdfOriginal', 'application/pdf'],
    ['COPY', 'e2e-cert-annotated.pdf', 'pdfCopy', 'application/pdf'],
    ['E_CERT', 'e2e-cert-scan.png', 'pngScan', 'image/png'],
  ];
  for (const [cat, fname, key, mime] of filePlan) {
    const f = CTX.files[key];
    if (!f) continue;
    const id = await T('§3 证照文件版本', '登记文件版本 · ' + cat, 'POST', '/bidding/certificate/file/register', {
      body: {
        certId: CTX.certIds[0],
        fileCategory: cat,
        fileName: fname,
        fileId: f.fileId,
        fileHash: f.sha256,
        fileFormat: mime,
        fileSize: f.size,
        copyCreationTime: Date.now(),
        copyCreator: cat === 'COPY' ? '西安市市场监督管理局' : '',
        copyCause: cat === 'COPY' ? '[E2E] 加注件登记' : '',
        copyExpiringTime: cat === 'COPY' ? Date.now() + 90 * 86400000 : undefined,
        remark: '[E2E] 文件版本登记 ' + cat,
      },
    });
    if (id !== null && id !== undefined) CTX.fileVersionIds.push(id);
  }
  if (CTX.fileVersionIds.length) {
    await T('§3 证照文件版本', '更正文件版本元数据', 'POST', '/bidding/certificate/file/correct', {
      body: {
        fileVersionId: CTX.fileVersionIds[0],
        remark: '[E2E] 更正：补充加注说明',
        copyCreationTime: Date.now(),
        copyCreator: '西安市市场监督管理局',
        copyCause: '[E2E] 更正加注原因',
        copyExpiringTime: Date.now() + 120 * 86400000,
      },
    });
    if (CTX.fileVersionIds.length > 1) {
      await T('§3 证照文件版本', '作废文件版本', 'POST', '/bidding/certificate/file/void', {
        body: { fileVersionId: CTX.fileVersionIds[1], reason: '[E2E] 作废验证：上传错版本' },
      });
    }
  }

  /* ---------- 7.4 验真 ---------- */
  if (CTX.certIds[3]) await T('§4 验真', '证照验真（单条）', 'POST', '/bidding/certificate-verify/verify', { body: { certId: CTX.certIds[3], verifyChannel: 'FOUR_PLATFORM' } });
  if (CTX.certIds[0]) await T('§4 验真', '证照文件验真', 'POST', '/bidding/certificate-verify/verify-file', { body: { certId: CTX.certIds[0], verifyChannel: 'MINISTRY' } });
  await T('§4 验真', '批量验真', 'POST', '/bidding/certificate-verify/verify-batch', {
    body: { certIds: CTX.certIds.slice(0, 3).map(String), verifyChannel: 'PROVINCE' },
  });

  /* ---------- 7.5 生命周期 ---------- */
  const evid = [fid('pdfEvidence'), fid('pdfNewCert')].filter((x) => x !== undefined);
  const lc = [
    {
      t: 'ANNUAL',
      body: {
        bizDomain: 'CERT', bizId: CTX.certIds[0], lifecycleType: 'ANNUAL', applyNo: 'E2E-LC-ANNUAL-' + ts,
        changeSummary: '[E2E] 年检结论登记', reviewYear: 2026, reviewDate: d(0), reviewResult: 1,
        evidenceFileIds: evid, remark: '[E2E] 年检生命周期',
      },
    },
    {
      t: 'CHANGE',
      body: {
        bizDomain: 'CERT', bizId: CTX.certIds[1], lifecycleType: 'CHANGE', applyNo: 'E2E-LC-CHANGE-' + ts,
        changes: [
          { fieldName: 'certScope', oldValue: '分公司经营场所备案', newValue: '[E2E] 扩展经营范围' },
          { fieldName: 'issuingAuthority', oldValue: '西安市市场监督管理局高新分局', newValue: '西安市市场监督管理局' },
        ],
        changeSummary: '[E2E] 变更经营范围', newCertFileId: fid('pdfNewCert'), evidenceFileIds: evid, remark: '[E2E] 变更生命周期',
      },
    },
    {
      t: 'RENEW',
      body: {
        bizDomain: 'CERT', bizId: CTX.certIds[2], lifecycleType: 'RENEW', applyNo: 'E2E-LC-RENEW-' + ts,
        renewalType: 'RENEW', newValidFrom: d(0), newValidTo: d(365), newNextReviewDate: d(300),
        newCertNo: 'E2E-CERT-0003-R', resultNo: 'E2E-RESULT-RENEW-' + ts, evidenceFileIds: evid,
        changeSummary: '[E2E] 体系认证续期', remark: '[E2E] 续期生命周期',
      },
    },
    {
      t: 'REVOKE',
      body: {
        bizDomain: 'CERT', bizId: CTX.certIds[4], lifecycleType: 'REVOKE', applyNo: 'E2E-LC-REVOKE-' + ts,
        revokeType: 'SUSPEND', revokeAuthority: '陕西省住房和城乡建设厅', revokeReason: '[E2E] 暂扣验证',
        revokeEffectiveAt: d(1), evidenceFileIds: evid, changeSummary: '[E2E] 吊销/暂扣', remark: '[E2E] 吊销生命周期',
      },
    },
  ];
  for (const item of lc) {
    if (!item.body.bizId) continue;
    const id = await T('§6 生命周期', '提交生命周期申请 · ' + item.t, 'POST', '/bidding/cert-lifecycle/apply', { body: item.body });
    if (id !== null && id !== undefined) CTX.lifecycleIds.push({ id, type: item.t, applyNo: item.body.applyNo });
  }
  /* 全字段一次性提交（正面覆盖「一个请求带齐所有可输入字段」的口径） */
  await T('§6 生命周期', '生命周期申请 · 全字段合并提交', 'POST', '/bidding/cert-lifecycle/apply', {
    body: {
      bizDomain: 'CERT', bizId: CTX.certIds[10], lifecycleType: 'CHANGE', applyNo: 'E2E-LC-ALL-' + ts,
      changes: [{ fieldName: 'certName', oldValue: '旧', newValue: '[E2E] 全字段' }],
      changeSummary: '[E2E] 全字段', renewalType: 'REISSUE', newValidFrom: d(0), newValidTo: d(400),
      newNextReviewDate: d(360), newCertNo: 'E2E-ALL-0001', resultNo: 'E2E-ALL-R-' + ts, revokeType: 'REVOKE',
      revokeAuthority: '陕西省住房和城乡建设厅', revokeReason: '[E2E] 全字段', revokeEffectiveAt: d(2),
      reviewYear: 2026, reviewDate: d(0), reviewResult: 0, newCertFileId: fid('pdfNewCert'),
      evidenceFileIds: evid, remark: '[E2E] 生命周期全字段合并提交',
    },
  }, '契约口径：字段按 lifecycleType 分支生效，合并提交仅用于核对字段映射，允许校验拦截');

  /* 审核 + 生效 */
  const lifecycleVersion = async (id) => {
    const r = await rawCall('GET', '/bidding/cert-lifecycle/get', { query: { id } });
    return r.json && r.json.data && typeof r.json.data.version === 'number' ? r.json.data.version : 0;
  };
  if (CTX.lifecycleIds.length) {
    const first = CTX.lifecycleIds[0];
    await T('§6 生命周期', '审核生命周期（通过）', 'POST', '/bidding/cert-lifecycle/audit', {
      body: { id: first.id, targetStatus: 'APPROVED', version: await lifecycleVersion(first.id), remark: '[E2E] 审核通过' },
    });
    await T('§6 生命周期', '生命周期生效', 'POST', '/bidding/cert-lifecycle/effect', {
      body: { id: first.id, version: await lifecycleVersion(first.id), remark: '[E2E] 生效执行' },
    });
    if (CTX.lifecycleIds.length > 1) {
      await T('§6 生命周期', '审核生命周期（驳回）', 'POST', '/bidding/cert-lifecycle/audit', {
        body: { id: CTX.lifecycleIds[1].id, targetStatus: 'REJECTED', version: await lifecycleVersion(CTX.lifecycleIds[1].id), remark: '[E2E] 驳回验证' },
      });
    }
    /* 催办只对「待办」态合法：新开一条不改状态，直接催办 */
    const pendingId = await T('§6 生命周期', '提交生命周期申请 · RENEW（供催办）', 'POST', '/bidding/cert-lifecycle/apply', {
      body: {
        bizDomain: 'CERT', bizId: CTX.certIds[6], lifecycleType: 'RENEW', applyNo: 'E2E-LC-URGE-' + ts,
        renewalType: 'RENEW', newValidFrom: d(0), newValidTo: d(365), newNextReviewDate: d(300),
        newCertNo: 'E2E-CERT-0007-R', resultNo: 'E2E-RESULT-URGE-' + ts, evidenceFileIds: evid,
        changeSummary: '[E2E] 供催办的待办单', remark: '[E2E] 待催办生命周期',
      },
    });
    if (pendingId !== null && pendingId !== undefined) CTX.lifecycleIds.push({ id: pendingId, type: 'RENEW-URGE', applyNo: 'E2E-LC-URGE-' + ts });
    await T('§6 生命周期', '生命周期催办', 'POST', '/bidding/cert-lifecycle/urge', {
      body: { id: pendingId !== null && pendingId !== undefined ? pendingId : first.id, remark: '[E2E] 催办验证' },
    }, pendingId === null || pendingId === undefined ? '前置：待办单创建失败，回退催办首条' : '');
  }

  /* ---------- 7.6 资质：标准 + 主档 + 要素 + 核查 + 安许 ---------- */
  for (let i = 0; i < 4; i++) {
    const q = QUAL_SEEDS[i];
    const id = await T('§11 资质', '登记资质标准 · ' + q[0] + '/' + q[1], 'POST', '/bidding/qualification/standard/create', {
      body: {
        qualSerial: q[0], qualCategory: q[1], qualGrade: q[2], stdVersion: 'E2E-STD-V' + (i + 1),
        reqStaff: JSON.stringify([{ role: 'REGISTERED_BUILDER', count: 2 + i }, { role: 'TECH_LEAD', count: 1 }]),
        reqAssets: JSON.stringify([{ type: 'NET_ASSET', min: 800 + i * 100 }]),
        reqPerformance: JSON.stringify([{ role: 'GENERAL', minAmount: 5000000 + i * 1000000 }]),
        reqEquipment: JSON.stringify([{ name: '塔式起重机', count: 2 }]),
        effectiveFrom: d(-180), effectiveTo: d(540), sourceRef: '[E2E] 资质标准来源 ' + (i + 1),
      },
    });
    if (id !== null && id !== undefined) CTX.standardIds.push(id);
  }

  for (let i = 0; i < QUAL_SEEDS.length; i++) {
    const q = QUAL_SEEDS[i];
    const certIdx = i % CTX.certIds.length;
    const id = await T('§11 资质', '创建企业资质 · ' + q[0] + '/' + q[2], 'POST', '/bidding/qualification/create', {
      body: {
        entId: ENT_ID, qualSerial: q[0], qualCategory: q[1], qualGrade: q[2],
        certNo: 'E2E-QUAL-' + String(i + 1).padStart(4, '0'),
        issuingAuthority: '陕西省住房和城乡建设厅', issueDate: d(-365),
        validTo: q[3], approvalScope: '[E2E] 许可范围 ' + (i + 1),
        bindedCertId: CTX.certIds[certIdx], remark: '[E2E] 全量端到端测试数据 资质' + (i + 1),
      },
    });
    if (id !== null && id !== undefined) CTX.qualIds.push(id);
  }
  if (!CTX.qualIds.length) throw new Error('造数中断：企业资质创建全部失败');

  /* 续期申报 + 审批/退回 */
  for (let i = 0; i < 3; i++) {
    const id = await T('§11 资质', '资质延续申报', 'POST', '/bidding/qualification/renew', {
      body: { qualificationId: CTX.qualIds[i], version: 0, dueDate: d(30 + i * 10), remark: '[E2E] 延续申报 ' + (i + 1) },
    });
    if (id !== null && id !== undefined) CTX.applyIds.push(id);
  }
  if (CTX.applyIds[0]) {
    await T('§11 资质', '延续申报批准', 'POST', '/bidding/qualification/approve', {
      body: { applyId: CTX.applyIds[0], version: 0, resultNo: 'E2E-APPLY-R-' + ts, newCertNo: 'E2E-QUAL-0001-R', newValidTo: d(730) },
    });
  }
  if (CTX.applyIds[1]) {
    await T('§11 资质', '延续申报退回', 'POST', '/bidding/qualification/reject', {
      body: { applyId: CTX.applyIds[1], version: 0, remark: '[E2E] 退回：材料不齐' },
    });
  }

  /* 要素台账：人员配置 12 / 工程业绩 10 / 资产 8 */
  for (let i = 0; i < 12; i++) {
    const role = PERSON_SEEDS[i % PERSON_SEEDS.length][6];
    const id = await T('§11 资质', '录人员配置 · ' + role, 'POST', '/bidding/qualification/element/staff', {
      body: {
        entId: ENT_ID, qualId: CTX.qualIds[i % CTX.qualIds.length], personName: PERSON_SEEDS[i % PERSON_SEEDS.length][1],
        staffRole: role, major: PERSON_SEEDS[i % PERSON_SEEDS.length][5], regNo: 'E2E-REG-' + String(i + 1).padStart(4, '0'),
        titleLevel: i % 3 === 0 ? 'SENIOR' : 'MIDDLE', isFullTime: i % 4 !== 0, regValidTo: d(200 + i * 15),
        bindedCertId: CTX.certIds[i % CTX.certIds.length],
      },
    });
    if (id !== null && id !== undefined) CTX.staffIds.push(id);
  }
  for (let i = 0; i < 10; i++) {
    const id = await T('§11 资质', '录工程业绩', 'POST', '/bidding/qualification/element/performance', {
      body: {
        entId: ENT_ID, qualId: CTX.qualIds[i % CTX.qualIds.length], projectName: '[E2E] 业绩项目 ' + (i + 1),
        projectType: i % 2 === 0 ? 'BUILDING' : 'MUNICIPAL', role: i % 2 === 0 ? 'GENERAL' : 'SUB',
        contractAmount: 5000000 + i * 750000, completionDate: d(-90 - i * 30),
        qualityResult: '合格', ownerName: '西安某某建设投资有限公司', isPlatformVerified: i % 2 === 0,
        evidenceFileIds: String(fid('pdfPerf')),
      },
    });
    if (id !== null && id !== undefined) CTX.perfIds.push(id);
  }
  const assetPlan = ['NET_ASSET', 'PLANT', 'MAJOR_EQUIP', 'TEST_INSTR', 'NET_ASSET', 'MAJOR_EQUIP', 'PLANT', 'TEST_INSTR'];
  for (let i = 0; i < assetPlan.length; i++) {
    const id = await T('§11 资质', '录资产/技术装备 · ' + assetPlan[i], 'POST', '/bidding/qualification/element/asset', {
      body: {
        entId: ENT_ID, assetType: assetPlan[i], name: '[E2E] 资产设备 ' + (i + 1), spec: '规格-' + (i + 1),
        quantity: i + 1, valueAmount: 100000 * (i + 1), statYear: 2025, evidenceFileIds: String(fid('pdfPerf')),
      },
    });
    if (id !== null && id !== undefined) CTX.assetIds.push(id);
  }

  /* 达标核查 3 次 */
  for (let i = 0; i < 3; i++) {
    const id = await T('§11 资质', '执行达标核查', 'POST', '/bidding/qualification/review/run', {
      body: { qualId: CTX.qualIds[i], reviewType: ['SELF', 'DYNAMIC', 'PRE_APPLY'][i], linkedApplyId: CTX.applyIds[i] || null },
    });
    if (id !== null && id !== undefined) CTX.reviewIds.push(id);
  }

  /* 安许建档 4 条 + 状态流转 1 条 */
  const permitPlan = [
    ['E2E-PERMIT-0001', d(120)],
    ['E2E-PERMIT-0002', d(15)],
    ['E2E-PERMIT-0003', d(-10)],
    ['E2E-PERMIT-0004', '2099-12-31'],
  ];
  for (let i = 0; i < permitPlan.length; i++) {
    const id = await T('§11 资质', '安许建档 · ' + permitPlan[i][0], 'POST', '/bidding/qualification/permit/create', {
      body: {
        entId: ENT_ID, permitNo: permitPlan[i][0], issuingAuthority: '陕西省住房和城乡建设厅',
        issueDate: d(-300), validTo: permitPlan[i][1], legalPerson: '张建国',
        bindedCertId: CTX.certIds[i % CTX.certIds.length], remark: '[E2E] 安许建档 ' + (i + 1),
      },
    });
    if (id !== null && id !== undefined) CTX.permitIds.push(id);
  }
  if (CTX.permitIds.length) {
    await T('§11 资质', '安许状态流转', 'POST', '/bidding/qualification/permit/status', {
      body: { permitId: CTX.permitIds[0], version: 0, targetStatus: 'SUSPENDED', reason: '[E2E] 暂扣验证' },
    });
  }

  /* ---------- 7.7 人员档案 10 + 员工证书 12 + 履历 ---------- */
  for (let i = 0; i < PERSON_SEEDS.length; i++) {
    const p = PERSON_SEEDS[i];
    const id = await T('§12 人员', '建档员工 · ' + p[1], 'POST', '/bidding/personnel-profile/create', {
      body: {
        entId: ENT_ID, personNo: p[0], personName: p[1], idCard: p[2], idCardValidTo: d(365 * 3 + i),
        gender: p[3], birthDate: p[2].slice(6, 10) + '-' + p[2].slice(10, 12) + '-' + p[2].slice(12, 14),
        mobile: '139' + String(10000000 + i).slice(0, 8), email: 'e2e' + (i + 1) + '@txgy.example.com',
        eduLevel: p[4], eduMajor: p[5], school: '西安建筑科技大学', graduateDate: '20' + (10 + (i % 10)) + '-07-01',
        titleLevel: i % 3 === 0 ? 'SENIOR' : 'MIDDLE', titleMajor: p[5], workStartDate: '20' + (10 + (i % 8)) + '-08-01',
        joinDate: '2020-0' + ((i % 9) + 1) + '-01', deptName: ['工程管理部', '安全管理部', '技术部'][i % 3],
        positionName: ['项目经理', '技术负责人', '安全员'][i % 3], employmentType: i % 5 === 0 ? 'PART_TIME' : 'FULL_TIME',
        archiveNo: 'E2E-ARCH-' + String(i + 1).padStart(4, '0'), bindedAuthUserId: 1,
        remark: '[E2E] 全量端到端测试数据 员工' + (i + 1),
      },
    });
    if (id !== null && id !== undefined) CTX.personIds.push(id);
  }
  if (!CTX.personIds.length) throw new Error('造数中断：员工建档全部失败');
  const person = (i) => CTX.personIds[i % CTX.personIds.length];

  const certTypePlan = ['REG_PRACTICE_CERT', 'SAFETY_ASSESS_CERT', 'TITLE_CERT', 'EDUCATION_CERT', 'SKILL_CERT', 'TRAINING_CERT'];
  const subTypePlan = ['A', 'B', 'C', '一级', '二级', '住建厅'];
  for (let i = 0; i < 12; i++) {
    const id = await T('§12 人员', '登记员工证书 · ' + certTypePlan[i % 6], 'POST', '/bidding/personnel-certificate/create', {
      body: {
        entId: ENT_ID, personId: person(i), certType: certTypePlan[i % 6],
        certName: '[E2E] ' + certTypePlan[i % 6] + ' 证书 ' + (i + 1),
        certNo: 'E2E-PCERT-' + String(i + 1).padStart(4, '0'), certLevel: i % 2 === 0 ? '一级' : '二级',
        certSubType: subTypePlan[i % 6], nextReviewDate: d(30 + i * 20), major: PERSON_SEEDS[i % 10][5],
        eduLevel: PERSON_SEEDS[i % 10][4], issuingAuthority: '陕西省住房和城乡建设厅',
        issueDate: d(-500), validFrom: d(-500), validTo: i % 5 === 0 ? d(-20) : d(60 + i * 25),
        registerUnit: HOLDER, registerUnitCode: '91350100M000100Y43',
        registerNo: 'E2E-REGN-' + String(i + 1).padStart(4, '0'), platformVerified: i % 3 === 0,
        verifyUrl: 'https://e2e.example.com/verify/' + (i + 1), bindedCertId: CTX.certIds[i % CTX.certIds.length],
        fileId: fid('pdfPersonCert'), extraProps: JSON.stringify({ source: 'E2E', idx: i + 1 }),
        remark: '[E2E] 员工证书 ' + (i + 1),
      },
    });
    if (id !== null && id !== undefined) CTX.personCertIds.push(id);
  }
  if (CTX.personCertIds.length) {
    await T('§12 人员', '更新员工证书', 'PUT', '/bidding/personnel-certificate/update', {
      body: {
        id: CTX.personCertIds[0], version: 0, entId: ENT_ID, personId: person(0), certType: 'REG_PRACTICE_CERT',
        certName: '[E2E] 注册执业证书 1（已更新）', certNo: 'E2E-PCERT-0001', certLevel: '一级', certSubType: 'A',
        nextReviewDate: d(60), major: '土木工程', eduLevel: 'BACHELOR', issuingAuthority: '住房和城乡建设部',
        issueDate: d(-500), validFrom: d(-500), validTo: d(400), registerUnit: HOLDER,
        registerUnitCode: '91350100M000100Y43', registerNo: 'E2E-REGN-0001', platformVerified: true,
        verifyUrl: 'https://e2e.example.com/verify/1', bindedCertId: CTX.certIds[0], fileId: fid('pdfPersonCert'),
        extraProps: JSON.stringify({ source: 'E2E-update' }), remark: '[E2E] 员工证书 1（已更新）',
      },
    });
    await T('§12 人员', '员工证书状态流转', 'PUT', '/bidding/personnel-certificate/change-status', {
      body: { id: CTX.personCertIds[1], targetStatus: 'REVOKED', version: 0, reason: '[E2E] 吊销验证' },
    });
    await T('§12 人员', '批量续期', 'POST', '/bidding/personnel-certificate/batch-renew', {
      body: {
        items: CTX.personCertIds.slice(0, 2).map((id) => ({ id, newValidTo: d(500), renewalType: 'RENEW', version: 0 })),
        certIds: CTX.personCertIds.slice(2, 3), newValidTo: d(500), renewalType: 'REISSUE', remark: '[E2E] 批量续期',
      },
    });
    await T('§12 人员', '到期提醒通知', 'POST', '/bidding/personnel-certificate/alert/notify', {
      body: { personIds: CTX.personIds.slice(0, 3), certIds: CTX.personCertIds.slice(0, 3), message: '[E2E] 证书到期提醒' },
    });
    await T('§12 人员', '员工证书验真', 'POST', '/bidding/personnel-certificate/verify', {
      body: { certId: CTX.personCertIds[0] },
    });
    await T('§12 人员', '员工证书导入', 'POST', '/bidding/personnel-certificate/import', {
      body: { fileId: fid('csvCert') },
    });
    /* 额外造一条专供删除验证 */
    const delId = await T('§12 人员', '登记待删除证书', 'POST', '/bidding/personnel-certificate/create', {
      body: {
        entId: ENT_ID, personId: person(1), certType: 'OTHER', certName: '[E2E] 待删除证书', certNo: 'E2E-PCERT-DEL',
        certLevel: '无', certSubType: '其他', nextReviewDate: d(90), major: '其他', eduLevel: 'OTHER',
        issuingAuthority: 'E2E 测试机构', issueDate: d(-10), validFrom: d(-10), validTo: d(90),
        registerUnit: HOLDER, registerUnitCode: '91350100M000100Y43', registerNo: 'E2E-REGN-DEL',
        platformVerified: false, verifyUrl: 'https://e2e.example.com/verify/del', bindedCertId: CTX.certIds[0],
        fileId: fid('pdfPersonCert'), extraProps: JSON.stringify({ source: 'E2E-del' }), remark: '[E2E] 待删除',
      },
    });
    if (delId !== null && delId !== undefined) {
      await T('§12 人员', '删除员工证书', 'DELETE', '/bidding/personnel-certificate/delete', { query: { id: delId } });
    }
    await T('§12 人员', '员工档案状态流转', 'PUT', '/bidding/personnel-profile/change-status', {
      body: { id: CTX.personIds[2], targetStatus: 'SUSPENDED', version: 0, reason: '[E2E] 停职验证' },
    });
    await T('§12 人员', '更新员工档案', 'PUT', '/bidding/personnel-profile/update', {
      body: {
        id: CTX.personIds[0], version: 0, entId: ENT_ID, personNo: PERSON_SEEDS[0][0], personName: PERSON_SEEDS[0][1],
        idCard: PERSON_SEEDS[0][2], idCardValidTo: d(1200), gender: 'MALE', birthDate: '1985-01-01',
        mobile: '13900000001', email: 'e2e1@txgy.example.com', eduLevel: 'BACHELOR', eduMajor: '土木工程',
        school: '西安建筑科技大学', graduateDate: '2010-07-01', titleLevel: 'SENIOR', titleMajor: '土木工程',
        workStartDate: '2010-08-01', joinDate: '2020-01-01', deptName: '工程管理部', positionName: '项目经理',
        employmentType: 'FULL_TIME', archiveNo: 'E2E-ARCH-0001', bindedAuthUserId: 1, remark: '[E2E] 员工 1（已更新）',
      },
    });
  }

  for (let i = 0; i < 10; i++) {
    await T('§12 人员', '录入培训记录', 'POST', '/bidding/personnel-record/training/create', {
      body: {
        entId: ENT_ID, personId: person(i), trainingName: '[E2E] 安全生产继续教育 ' + (i + 1),
        trainingType: ['SAFETY', 'SKILL', 'MANAGEMENT'][i % 3], organizer: '陕西省建设教育协会',
        startDate: d(-120 - i), endDate: d(-118 - i), eduHours: 16 + i, result: 'PASS', score: 80 + i,
        certNo: 'E2E-TRAIN-' + String(i + 1).padStart(4, '0'), certValidTo: d(300 + i * 10),
        fileId: fid('pdfTraining'), remark: '[E2E] 培训记录 ' + (i + 1),
      },
    });
  }
  for (let i = 0; i < 10; i++) {
    await T('§12 人员', '录入社保记录', 'POST', '/bidding/personnel-record/social-security/create', {
      body: {
        entId: ENT_ID, personId: person(i), payerName: HOLDER, payerCreditCode: '91350100M000100Y43',
        payMonth: '2026-0' + ((i % 9) + 1), payStatus: i % 7 === 0 ? 'SUSPENDED' : 'NORMAL',
        payBase: 6000 + i * 250, payAmount: 1800 + i * 60, socialNo: 'E2E-SI-' + String(i + 1).padStart(4, '0'),
        sourceChannel: 'MANUAL', evidenceFileId: fid('pdfSocial'), remark: '[E2E] 社保记录 ' + (i + 1),
      },
    });
  }
  for (let i = 0; i < 10; i++) {
    await T('§12 人员', '录入劳动合同', 'POST', '/bidding/personnel-record/labor-contract/create', {
      body: {
        entId: ENT_ID, personId: person(i), contractNo: 'E2E-LC-' + String(i + 1).padStart(4, '0'),
        contractType: i % 2 === 0 ? 'FIXED_TERM' : 'OPEN_ENDED', signDate: '2024-01-0' + ((i % 9) + 1),
        startDate: '2024-01-0' + ((i % 9) + 1), endDate: i % 2 === 0 ? d(365) : '2099-12-31',
        contractStatus: 'ACTIVE', positionName: ['项目经理', '技术负责人', '安全员'][i % 3],
        fileId: fid('pdfContract'), remark: '[E2E] 劳动合同 ' + (i + 1),
      },
    });
  }

  /* ---------- 7.8 CA 签章 ---------- */
  await T('§13 CA 签章', '演示签章数据生成', 'POST', '/bidding/ca/seal/demo-seed', { query: { count: 3 } });
  await T('§13 CA 签章', '演示篡改数据生成', 'POST', '/bidding/ca/seal/demo-tamper', { query: { count: 2 } });
  await T('§13 CA 签章', '清空演示签章数据', 'POST', '/bidding/ca/seal/demo-clear', {});

  const mkForm = (fileBuf, fname, mime, extra) => {
    const fd = new FormData();
    fd.append('file', new Blob([fileBuf], { type: mime }), fname);
    for (const k of Object.keys(extra || {})) fd.append(k, String(extra[k]));
    return fd;
  };
  const sealA = await T('§13 CA 签章', '文件签章 · 单文件', 'POST', '/bidding/ca/seal/document', {
    form: mkForm(pdfSealA, 'E2E-ca-seal-a.pdf', 'application/pdf', {
      businessCode: 'E2E-SEAL-BC-1', callbackUrl: 'http://127.0.0.1:48110/admin-api/bidding/ca/seal/callback',
    }),
    formFields: ['file', 'businessCode', 'callbackUrl'],
  });
  const sealB = await T('§13 CA 签章', '文件签章 · 单文件 PNG', 'POST', '/bidding/ca/seal/document', {
    form: mkForm(pngSealB, 'E2E-ca-seal-b.png', 'image/png', { businessCode: 'E2E-SEAL-BC-2' }),
    formFields: ['file', 'businessCode'],
  });
  if (sealA && sealA.documentId) CTX.sealIds.push(sealA.documentId);
  if (sealB && sealB.documentId) CTX.sealIds.push(sealB.documentId);

  const batchForm = new FormData();
  batchForm.append('files', new Blob([pdfSealA], { type: 'application/pdf' }), 'E2E-batch-1.pdf');
  batchForm.append('files', new Blob([pdfSealA], { type: 'application/pdf' }), 'E2E-batch-2.pdf');
  batchForm.append('businessCode', 'E2E-SEAL-BATCH');
  batchForm.append('callbackUrl', 'http://127.0.0.1:48110/admin-api/bidding/ca/seal/callback');
  const batch = await T('§13 CA 签章', '文件签章 · 批量', 'POST', '/bidding/ca/seal/batch', {
    form: batchForm,
    formFields: ['files', 'businessCode', 'callbackUrl'],
  });
  if (Array.isArray(batch)) for (const b of batch) if (b && b.documentId) CTX.sealIds.push(b.documentId);

  /* ---------- 7.9 规则配置（阈值来源） ---------- */
  await T('§14 规则配置', '保存规则配置', 'POST', '/bidding/rule-config/save', {
    body: {
      ruleKey: 'cert.expiring.window.days', configValue: '75', valueType: 'INT', ruleGroup: 'CERT',
      description: '[E2E] 证照到期预警窗口天数', regulationRef: '《电子证照模块-前端接口契约》§5',
      legalFloor: '30', changeReason: '[E2E] 全量端到端测试调整阈值', status: 'PUBLISHED', grayScope: 'ALL',
      effectiveFrom: Date.now(), effectiveTo: Date.now() + 365 * 86400000, version: 0,
    },
  });
  CTX.ruleSaved = true;

  /* ---------- 7.10 主体（授权 + 经办用户） ---------- */
  await T('§15 主体', '保存本企业主体档案（全字段，含 regCapCurrency）', 'POST', '/bidding/subject/profile/save', {
    body: {
      subjectType: 'BIDDER', subjectName: HOLDER, uscc: '91350100M000100Y43', legalPersonName: '张建国',
      legalPersonTypeCode: 'A', regionCode: '610100', industryCode: 'E47', regCapital: 50000000,
      regCapCurrency: 'CNY', contactName: '李红梅', contactPhone: '13900000002',
      licenseFileId: Number(fid('pdfOriginal')), licenseFileName: 'E2E-cert-original.pdf',
      extJson: JSON.stringify({ e2e: true, ts }), remark: '[E2E] 主体档案全量测试',
    },
  }, '归因用：单独传 regCapCurrency=CNY 即报 1060106039，其余字段均合法');
  await T('§15 主体', '保存本企业主体档案（去掉 regCapCurrency）', 'POST', '/bidding/subject/profile/save', {
    body: {
      subjectType: 'BIDDER', subjectName: HOLDER, uscc: '91350100M000100Y43', legalPersonName: '张建国',
      legalPersonTypeCode: 'A', regionCode: '610100', industryCode: 'E47', regCapital: 50000000,
      contactName: '李红梅', contactPhone: '13900000002',
      licenseFileId: Number(fid('pdfOriginal')), licenseFileName: 'E2E-cert-original.pdf',
      extJson: JSON.stringify({ e2e: true, ts, regCapCurrency: 'CNY' }), remark: '[E2E] 主体档案全量测试（币种改由 extJson 承载）',
    },
  });
  const authId = await T('§15 主体', '平台授权企业主体', 'POST', '/bidding/subject-auth/authorize', {
    body: { tenantId: 1, tenantName: HOLDER, subjectType: 'BIDDER', remark: '[E2E] 授权验证' },
  });
  if (authId !== null && authId !== undefined) CTX.authId = authId;
  if (CTX.authId) {
    await T('§15 主体', '授权状态流转 · 停用', 'PUT', '/bidding/subject-auth/status', { query: { id: CTX.authId, authStatus: 'DISABLED' } });
    await T('§15 主体', '授权状态流转 · 启用', 'PUT', '/bidding/subject-auth/status', { query: { id: CTX.authId, authStatus: 'ENABLED' } });
  }
  const bindId = await T('§15 主体', '绑定经办用户', 'POST', '/bidding/subject-auth/user/bind', {
    body: { tenantId: 1, subjectType: 'BIDDER', userId: 1, userRole: 'OPERATOR', remark: '[E2E] 经办用户绑定' },
  });
  if (bindId !== null && bindId !== undefined) {
    await T('§15 主体', '解绑经办用户', 'DELETE', '/bidding/subject-auth/user/unbind', { query: { id: bindId } });
  }

  /* ---------- 7.11 文件补登记 ---------- */
  if (CTX.files.pdfOriginal && CTX.files.pdfOriginal.url) {
    await T('§10 文件', '按 URL 补登记 file_id', 'POST', '/bidding/file/register-by-url', {
      query: { url: CTX.files.pdfOriginal.url },
    });
  }

  CTX.countsAfter = seedSnapshot();
  console.log('\n造数完成：证照 ' + CTX.certIds.length + ' 条 / 资质 ' + CTX.qualIds.length + ' 条 / 员工 ' + CTX.personIds.length + ' 名 / 员工证书 ' + CTX.personCertIds.length + ' 张 / 生命周期 ' + CTX.lifecycleIds.length + ' 条 / 安许 ' + CTX.permitIds.length + ' 条 / CA 签章 ' + CTX.sealIds.length + ' 条');
  fs.writeFileSync(STATE_FILE, JSON.stringify(CTX, null, 2), 'utf8');
  return CTX;
}


/* ============================ 8. 全量接口遍历 ============================ */

async function testAll() {
  console.log('\n================ 8. 全量接口遍历 ================');
  const C = CTX.certIds;
  const Q = CTX.qualIds;
  const P = CTX.personIds;
  const PC = CTX.personCertIds;
  const fid = (k) => (CTX.files[k] ? CTX.files[k].fileId : undefined);

  /* ---------- §1 元数据 ---------- */
  await T('§1 元数据', '字典（多类型）', 'GET', '/bidding/meta/dicts', {
    query: { types: 'cert_status,cert_type,cert_category,file_category,holder_type,verification_status,verify_channel,biz_domain,lifecycle_type,lifecycle_status' },
  });
  await T('§1 元数据', '业务阈值与规则配置', 'GET', '/bidding/meta/configs', { query: { group: 'CERT', keys: 'cert.expiring.window.days,cert.file.allow.formats' } });
  for (const t of ['CERTIFICATE', 'QUALIFICATION', 'PERSONNEL', 'PERSONNEL_CERT', 'SAFETY_PERMIT', 'SUBJECT', 'PROJECT']) {
    await T('§1 元数据', '统一候选选项 · ' + t, 'GET', '/bidding/meta/options', { query: { type: t, limit: 5 } });
  }

  /* ---------- §2 证照台账 ---------- */
  await T('§2 证照台账', '分页（全筛选字段）', 'GET', '/bidding/certificate/page', {
    query: {
      pageNo: 1, pageSize: 10, certName: 'E2E', certNo: 'E2E', certCategory: 'IDENTITY', certType: 'ADMIN_LICENSE',
      certStatus: 'ACTIVE', holderName: HOLDER, verificationStatus: 'UNVERIFIED', companyId: ENT_ID,
      validToFrom: d(-500), validToTo: d(1000), validityState: 'VALID',
    },
  });
  await T('§2 证照台账', '分页（无筛选）', 'GET', '/bidding/certificate/page', { query: { pageNo: 1, pageSize: 5 } });
  if (C[0]) await T('§2 证照台账', '详情', 'GET', '/bidding/certificate/get', { query: { id: C[0] } });
  await T('§2 证照台账', '看板统计', 'GET', '/bidding/certificate/stats', {});

  /* ---------- §3 文件版本 ---------- */
  if (C[0]) await T('§3 证照文件版本', '文件版本列表', 'GET', '/bidding/certificate/file/list', { query: { certId: C[0] } });

  /* ---------- §4 验真 ---------- */
  await T('§4 验真', '验真历史分页', 'GET', '/bidding/certificate-verify/history/page', {
    query: { pageNo: 1, pageSize: 10, certId: C[3], verifyChannel: 'FOUR_PLATFORM', verifyResult: 'MATCH' },
  });

  /* ---------- §5 预警 ---------- */
  await T('§5 预警', '预警扫描（补跑）', 'POST', '/bidding/certificate-alert-run/scan', {});
  await T('§5 预警', '预警派发（补跑）', 'POST', '/bidding/certificate-alert-run/dispatch', {});
  await T('§5 预警', '预警清单分页', 'GET', '/bidding/certificate-alert-run/alert/page', {
    query: { pageNo: 1, pageSize: 10, entId: ENT_ID, certId: C[0], alertType: 'EXPIRY', alertLevel: 'WARNING', handled: false },
  });

  /* ---------- §6 生命周期 ---------- */
  if (CTX.lifecycleIds[0]) await T('§6 生命周期', '生命周期详情', 'GET', '/bidding/cert-lifecycle/get', { query: { id: CTX.lifecycleIds[0].id } });
  await T('§6 生命周期', '生命周期分页', 'GET', '/bidding/cert-lifecycle/page', {
    query: { pageNo: 1, pageSize: 10, bizDomain: 'CERT', bizId: C[0], lifecycleType: 'ANNUAL', status: 'PENDING', applyNo: 'E2E-LC' },
  });
  if (C[0]) await T('§6 生命周期', '按对象查轨迹', 'GET', '/bidding/cert-lifecycle/list-by-biz', { query: { bizDomain: 'CERT', bizId: C[0] } });

  /* ---------- §7 材料完备性 ---------- */
  await T('§7 材料完备性', '材料完备性自查', 'GET', '/bidding/enterprise-material/completeness', { query: { entId: ENT_ID, asOf: d(0) } });

  /* ---------- §8 导入 ---------- */
  await T('§8 导入', '证照批量导入', 'POST', '/bidding/certificate/import', { body: { fileId: fid('csvCert') } });

  /* ---------- §10 文件读出口 ---------- */
  await T('§10 文件', '文件内联预览/下载', 'GET', '/bidding/file/download', { query: { id: fid('pdfOriginal') } });

  /* ---------- §11 资质 ---------- */
  await T('§11 资质', '资质分页（全筛选字段）', 'GET', '/bidding/qualification/page', {
    query: {
      pageNo: 1, pageSize: 10, entId: ENT_ID, qualSerial: 'CONSTRUCTION', qualCategory: 'GENERAL_CONTRACTING',
      qualGrade: 'FIRST', qualStatus: 'VALID', expiringInDays: 365, keyword: 'E2E', companyId: ENT_ID,
      validToFrom: d(-500), validToTo: d(1000),
    },
  });
  if (Q[0]) await T('§11 资质', '资质详情', 'GET', '/bidding/qualification/get', { query: { id: Q[0] } });
  if (Q[0]) {
    const qd = await rawCall('GET', '/bidding/qualification/get', { query: { id: Q[0] } });
    const qv = qd.json && qd.json.data && typeof qd.json.data.version === 'number' ? qd.json.data.version : 0;
    await T('§11 资质', '修改企业资质（全字段+乐观锁）', 'PUT', '/bidding/qualification/update', {
      body: {
        id: Q[0], version: qv, entId: ENT_ID, qualSerial: 'CONSTRUCTION', qualCategory: 'GENERAL_CONTRACTING',
        qualGrade: 'FIRST', certNo: 'E2E-QUAL-0001', issuingAuthority: '陕西省住房和城乡建设厅',
        issueDate: d(-365), validTo: d(180), approvalScope: '[E2E] 许可范围（已更新）',
        bindedCertId: C[0], remark: '[E2E] 资质 1（已更新）',
      },
    });
  }
  await T('§11 资质', '申报记录分页', 'GET', '/bidding/qualification/apply/page', {
    query: { pageNo: 1, pageSize: 10, entId: ENT_ID, qualId: Q[0], applyType: 'RENEW', status: 'SUBMITTED' },
  });
  await T('§11 资质', '资质标准分页', 'GET', '/bidding/qualification/standard/page', {
    query: { pageNo: 1, pageSize: 10, qualSerial: 'CONSTRUCTION', qualCategory: 'GENERAL_CONTRACTING', qualGrade: 'FIRST', stdVersion: 'E2E-STD-V1', onlyEffective: true },
  });
  await T('§11 资质', '资质维度选项', 'GET', '/bidding/qualification/standard/options', {});
  await T('§11 资质', '要素·人员配置分页', 'GET', '/bidding/qualification/element/staff/page', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID, qualId: Q[0] } });
  await T('§11 资质', '要素·工程业绩分页', 'GET', '/bidding/qualification/element/performance/page', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID, qualId: Q[0] } });
  await T('§11 资质', '要素·资产装备分页', 'GET', '/bidding/qualification/element/asset/page', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID, qualId: Q[0] } });
  await T('§11 资质', '达标核查历史分页', 'GET', '/bidding/qualification/review/page', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID, qualId: Q[0], reviewResult: 'PASS' } });
  await T('§11 资质', '安许证分页', 'GET', '/bidding/qualification/permit/page', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID, permitStatus: 'VALID', expiringInDays: 365, keyword: 'E2E' } });
  if (CTX.permitIds[0]) await T('§11 资质', '安许证详情', 'GET', '/bidding/qualification/permit/get', { query: { id: CTX.permitIds[0] } });
  await T('§11 资质', '到期/风险预警清单（带筛选）', 'GET', '/bidding/qualification/alert/page', {
    query: { pageNo: 1, pageSize: 10, entId: ENT_ID, refType: 'QUAL', alertLevel: 'WARNING', handled: false },
  });
  await T('§11 资质', '资质预警扫描（三源）', 'POST', '/bidding/qualification-alert-run/scan', {});
  const qAlertAll = await T('§11 资质', '到期/风险预警清单（取待处理行）', 'GET', '/bidding/qualification/alert/page', {
    query: { pageNo: 1, pageSize: 10, handled: false },
  });
  const qAlertRow = qAlertAll && Array.isArray(qAlertAll.list) && qAlertAll.list.length ? qAlertAll.list[0] : null;
  await T('§11 资质', '预警销账（处理）', 'POST', '/bidding/qualification/alert/handle', {
    body: { alertId: qAlertRow ? qAlertRow.id : 0, handleNote: '[E2E] 已处理', version: qAlertRow ? qAlertRow.version || 0 : 0 },
  }, qAlertRow ? '' : '前置：无待处理预警行，用占位 alertId 触发一次以保证接口被覆盖');
  await T('§11 资质', '资质看板统计', 'GET', '/bidding/qualification/stats', { query: { entId: ENT_ID } });
  await T('§11 资质', '资质批量导入', 'POST', '/bidding/qualification/import', { body: { fileId: fid('csvQual') } });
  await T('§11 资质', '资质导出 Excel', 'GET', '/bidding/qualification/export-excel', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID } });

  /* ---------- §12 人员 ---------- */
  await T('§12 人员', '员工证书分页（全筛选字段）', 'GET', '/bidding/personnel-certificate/page', {
    query: { pageNo: 1, pageSize: 10, personId: P[0], entId: ENT_ID, certType: 'REG_PRACTICE_CERT', certNo: 'E2E-PCERT', certStatus: 'VALID', major: '土木工程', expiringDays: 365 },
  });
  await T('§12 人员', '某员工全部证书', 'GET', '/bidding/personnel-certificate/list-by-person', { query: { personId: P[0] } });
  await T('§12 人员', '到期证书清单', 'GET', '/bidding/personnel-certificate/list-expiring', { query: { entId: ENT_ID, days: 90 } });
  await T('§12 人员', '按细分统计持证人数', 'GET', '/bidding/personnel-certificate/count-by-sub-type', { query: { entId: ENT_ID, certType: 'SAFETY_ASSESS_CERT' } });
  await T('§12 人员', '按人聚合分页', 'GET', '/bidding/personnel-certificate/page-by-person', { query: { pageNo: 1, pageSize: 10, deptName: '工程管理部', certType: 'REG_PRACTICE_CERT', certStatus: 'VALID', expiringDays: 365, companyId: ENT_ID, entId: ENT_ID } });
  await T('§12 人员', '人员证书统计', 'GET', '/bidding/personnel-certificate/stats', { query: { entId: ENT_ID } });
  await T('§12 人员', '法定预警下限', 'GET', '/bidding/personnel-alert-run/legal-floor-days', {});
  await T('§12 人员', '人员预警扫描', 'POST', '/bidding/personnel-alert-run/scan', {});
  await T('§12 人员', '人员预警清单', 'GET', '/bidding/personnel-alert-run/alert/page', {
    query: { pageNo: 1, pageSize: 10, entId: ENT_ID, refType: 'PERSON_CERT', alertType: 'EXPIRY', alertLevel: 'WARNING', handled: false },
  });
  await T('§12 人员', '员工档案分页', 'GET', '/bidding/personnel-profile/page', {
    query: { pageNo: 1, pageSize: 10, entId: ENT_ID, personNo: 'E2E-P', personName: '张', deptName: '工程管理部', personnelStatus: 'ACTIVE', eduLevel: 'BACHELOR', titleLevel: 'SENIOR', certType: 'REG_PRACTICE_CERT' },
  });
  if (P[0]) await T('§12 人员', '员工档案详情', 'GET', '/bidding/personnel-profile/get', { query: { id: P[0] } });
  if (P[0]) await T('§12 人员', '履职概览（按前端入参 id）', 'GET', '/bidding/personnel-profile/overview', { query: { id: P[0] } }, '前端 PersonnelProfileApi 传 id，后端要求 personId —— 契约不一致，见失败归因');
  if (P[0]) await T('§12 人员', '履职概览（按后端入参 personId）', 'GET', '/bidding/personnel-profile/overview', { query: { personId: P[0] } });
  await T('§12 人员', '培训记录列表', 'GET', '/bidding/personnel-record/training/list', { query: { personId: P[0] } });
  await T('§12 人员', '社保记录列表', 'GET', '/bidding/personnel-record/social-security/list', { query: { personId: P[0] } });
  await T('§12 人员', '劳动合同列表', 'GET', '/bidding/personnel-record/labor-contract/list', { query: { personId: P[0] } });

  /* ---------- §13 CA 签章 / 互认 ---------- */
  await T('§13 CA 签章', '签章台账分页', 'GET', '/bidding/ca/seal/page', {
    query: { pageNo: 1, pageSize: 10, documentName: 'E2E', orgName: 'CA', verifyStatus: 'PASS', trackId: '' },
  });
  await T('§13 CA 签章', '签章统计', 'GET', '/bidding/ca/seal/stats', {});
  const sealPage = await T('§13 CA 签章', '签章台账分页（取 id 供后续）', 'GET', '/bidding/ca/seal/page', { query: { pageNo: 1, pageSize: 5 } });
  const sealRow = sealPage && Array.isArray(sealPage.list) && sealPage.list.length ? sealPage.list[0] : null;
  if (sealRow) {
    await T('§13 CA 签章', '签章详情', 'GET', '/bidding/ca/seal/get', { query: { id: sealRow.id } });
    await T('§13 CA 签章', '签章验签', 'POST', '/bidding/ca/seal/verify', { query: { id: sealRow.id } });
  } else {
    await T('§13 CA 签章', '签章详情', 'GET', '/bidding/ca/seal/get', { query: { id: 0 } }, '前置：台账为空，占位');
    await T('§13 CA 签章', '签章验签', 'POST', '/bidding/ca/seal/verify', { query: { id: 0 } }, '前置：台账为空，占位');
  }
  await T('§13 CA 签章', 'CA 认证检测', 'GET', '/bidding/ca/seal/check', {
    query: { fileId: fid('pdfSealA'), sha256: CTX.files.pdfSealA ? CTX.files.pdfSealA.sha256 : '' },
  });
  await T('§13 CA 签章', '签章完成回调', 'POST', '/bidding/ca/seal/callback', {
    body: {
      businessCode: 'E2E-SEAL-BC-1', documentId: sealRow ? sealRow.documentId : 'E2E-SEAL-DOC-UNKNOWN',
      signature: sealRow && sealRow.signature ? sealRow.signature : 'E2E-SIGNATURE-PLACEHOLDER',
      certSn: sealRow && sealRow.certSn ? sealRow.certSn : 'E2E-CERT-SN', hash: 'e2e-hash', sha256: 'e2e-sha256',
    },
  }, '负向用例：回调体签名与文件 SM3 指纹不匹配，被验签拒绝属预期');
  await T('§13 CA 互认', '签发官方调起参数', 'POST', '/bidding/ca/share/launch-param', {
    body: {
      awaitingSignatureFileName: 'E2E-ca-seal-a.pdf',
      awaitingSignatureFilePath: CTX.files.pdfSealA ? CTX.files.pdfSealA.url : 'http://127.0.0.1:48080/admin-api/infra/file/4/get/e2e.pdf',
      fileType: 'pdf', signatureFileName: 'E2E-ca-seal-a_sealed.pdf',
      signatureFileSavePath: 'bidding/seal/result/E2E-ca-seal-a_sealed.pdf',
    },
  });
  await T('§13 CA 互认', '官方回调连通性自测 · H5', 'POST', '/bidding/ca/share/seal/callback', {
    body: {
      action: 'allDone',
      data: { signatureInfo: { completedSignatureTime: '', document: [] }, status: '20', transactionId: 'e2e-probe-' + Date.now() },
    },
  }, '官方端点：以 {code:200,success:true} 表示成功，不套 CommonResult');
  await T('§13 CA 互认', '官方回调连通性自测 · 客户端', 'POST', '/bidding/ca/share/seal/client-callback', {
    body: { callbackRequestType: 'signedAndVerified', document: [], tradingSystemCode: '' },
  }, '官方端点：以 {code:200,success:true} 表示成功，不套 CommonResult');
  const sm3 = await T('§13 CA 通用', 'SM3 摘要', 'GET', '/bidding/ca/sm3', { query: { text: 'E2E-电子证照全量测试' } });
  await T('§13 CA 通用', 'CA 身份认证', 'POST', '/bidding/ca/authenticate', { query: { caCertNo: 'E2E-CA-CERT-0001', subjectCode: '91350100M000100Y43' } });
  const qr = await T('§13 CA 通用', '生成签名二维码', 'POST', '/bidding/ca/sign/qrcode', { query: { digestHex: typeof sm3 === 'string' ? sm3 : '0'.repeat(64) } });
  const tid = qr && (qr.tid || qr.transactionId || qr.tid2) ? qr.tid || qr.transactionId : null;
  if (tid) {
    await T('§13 CA 通用', '查询签名状态', 'GET', '/bidding/ca/sign/status', { query: { tid } });
    const sim = await T('§13 CA 通用', '本地测试密钥补签', 'POST', '/bidding/ca/sign/simulate', { query: { tid } });
    await T('§13 CA 通用', '取签名值', 'GET', '/bidding/ca/sign/value', { query: { tid } });
    await T('§13 CA 通用', '验签', 'POST', '/bidding/ca/verify', {
      query: {
        digestHex: typeof sm3 === 'string' ? sm3 : '0'.repeat(64),
        signatureBase64: sim && sim.signature ? sim.signature : 'E2E-SIGNATURE-PLACEHOLDER',
        publicKeyAlgorithm: sim && sim.publicKeyAlgorithm ? sim.publicKeyAlgorithm : 'SM2',
      },
    }, sim && sim.signature ? '' : '前置：补签未返回 signature，用占位签名保证接口被覆盖');
  } else {
    await T('§13 CA 通用', '查询签名状态', 'GET', '/bidding/ca/sign/status', { query: { tid: 'E2E-TID-PLACEHOLDER' } }, '前置：二维码接口未返回 tid，占位');
    await T('§13 CA 通用', '本地测试密钥补签', 'POST', '/bidding/ca/sign/simulate', { query: { tid: 'E2E-TID-PLACEHOLDER' } }, '前置：二维码接口未返回 tid，占位');
    await T('§13 CA 通用', '取签名值', 'GET', '/bidding/ca/sign/value', { query: { tid: 'E2E-TID-PLACEHOLDER' } }, '前置：二维码接口未返回 tid，占位');
    await T('§13 CA 通用', '验签', 'POST', '/bidding/ca/verify', {
      query: { digestHex: typeof sm3 === 'string' ? sm3 : '0'.repeat(64), signatureBase64: 'E2E-SIGNATURE-PLACEHOLDER', publicKeyAlgorithm: 'SM2' },
    }, '前置：二维码接口未返回 tid，用占位签名保证接口被覆盖');
  }
  const loginQr = await T('§13 CA 通用', '生成登录二维码', 'POST', '/bidding/ca/login/qrcode', {});
  const ltid = loginQr && loginQr.tid ? loginQr.tid : 'E2E-LOGIN-TID-PLACEHOLDER';
  await T('§13 CA 通用', '查询登录扫码状态', 'GET', '/bidding/ca/login/status', { query: { tid: ltid } });
  await T('§13 CA 通用', '取用户认证信息', 'GET', '/bidding/ca/login/auth-info', { query: { tid: ltid } });
  const decQr = await T('§13 CA 通用', '生成解密二维码', 'POST', '/bidding/ca/decrypt/qrcode', {
    query: { decryptionUserType: 'AGENCY', orgCode: '91350100M000100Y43', idCardHash: 'e2e-idcard-hash' },
  });
  const dtid = decQr && decQr.tid ? decQr.tid : 'E2E-DECRYPT-TID-PLACEHOLDER';
  await T('§13 CA 通用', '查询解密扫码状态', 'GET', '/bidding/ca/decrypt/status', { query: { tid: dtid } });
  await T('§13 CA 通用', '取解密用户信息', 'GET', '/bidding/ca/decrypt/user-info', { query: { tid: dtid } });
  await T('§13 CA 通用', '取解密数据', 'GET', '/bidding/ca/decrypt/data', { query: { tid: dtid } });

  /* ---------- §14 规则配置 ---------- */
  await T('§14 规则配置', '读取规则配置', 'GET', '/bidding/rule-config/get', { query: { ruleKey: 'cert.expiring.window.days' } });
  await T('§14 规则配置', '规则版本历史', 'GET', '/bidding/rule-config/history', { query: { ruleKey: 'cert.expiring.window.days' } });
  await T('§14 规则配置', '规则回滚', 'POST', '/bidding/rule-config/rollback', {
    query: { ruleKey: 'cert.expiring.window.days', targetVersion: 1, changeReason: '[E2E] 回滚验证' },
  });
  await T('§14 规则配置', '规则命中审计分页', 'GET', '/bidding/rule-config/audit/page', {
    query: { pageNo: 1, pageSize: 10, ruleKey: 'cert.expiring.window.days', hitSource: 'ALERT_SCAN', bizScene: 'CERT', readBy: 'admin', readAtFrom: d(-30) + ' 00:00:00', readAtTo: d(1) + ' 23:59:59' },
  });

  /* ---------- §15 主体 ---------- */
  await T('§15 主体', '本企业主体档案', 'GET', '/bidding/subject/profile/get', { query: { subjectType: 'BIDDER' } });
  await T('§15 主体', '本企业资格状态', 'GET', '/bidding/subject/profile/eligibility', { query: { subjectType: 'BIDDER' } });
  await T('§15 主体', '已授权主体分页', 'GET', '/bidding/subject-auth/page', {
    query: { pageNo: 1, pageSize: 10, tenantId: 1, subjectType: 'BIDDER', authStatus: 'ENABLED', tenantName: HOLDER },
  });
  await T('§15 主体', '指定企业资格状态', 'GET', '/bidding/subject-auth/eligibility', { query: { tenantId: 1, subjectType: 'BIDDER' } });
  await T('§15 主体', '主体经办用户分页', 'GET', '/bidding/subject-auth/user/page', {
    query: { pageNo: 1, pageSize: 10, tenantId: 1, subjectType: 'BIDDER', userRole: 'OPERATOR', enabled: true },
  });

  /* ---------- §16 所属企业（后端未实现，口径已定不做） ---------- */
  await T('§16 所属企业', '分页', 'GET', '/bidding/subject-company/page', { query: { pageNo: 1, pageSize: 10, companyName: 'E2E', uscc: '91350100M000100Y43', companyType: 'SELF', parentId: '', enabled: true } });
  await T('§16 所属企业', '详情', 'GET', '/bidding/subject-company/get', { query: { id: ENT_ID } });
  await T('§16 所属企业', '新增', 'POST', '/bidding/subject-company/create', {
    body: { companyName: HOLDER, uscc: '91350100M000100Y43', companyType: 'SELF', parentId: '', legalPersonName: '张建国', contactName: '李红梅', contactPhone: '13900000002', regionCode: '610100', industryCode: 'E47', remark: '[E2E]', enabled: true },
  });
  await T('§16 所属企业', '修改', 'PUT', '/bidding/subject-company/update', {
    body: { id: ENT_ID, version: 0, companyName: HOLDER, uscc: '91350100M000100Y43', companyType: 'SELF', parentId: '', legalPersonName: '张建国', contactName: '李红梅', contactPhone: '13900000002', regionCode: '610100', industryCode: 'E47', remark: '[E2E]', enabled: true },
  });
  await T('§16 所属企业', '启停', 'PUT', '/bidding/subject-company/status', { query: { id: ENT_ID, enabled: true } });
  await T('§16 所属企业', '删除', 'DELETE', '/bidding/subject-company/delete', { query: { id: ENT_ID } });

  /* ---------- §17 安全风险 / 风险规则（后端未实现） ---------- */
  await T('§17 安全风险', '风险条目分页', 'GET', '/bidding/safety-risk/page', { query: { pageNo: 1, pageSize: 10, riskLevel: 'HIGH', status: 'PENDING', assigneeId: '1', keyword: 'E2E', entId: ENT_ID } });
  await T('§17 安全风险', '风险详情', 'GET', '/bidding/safety-risk/get', { query: { id: '1' } });
  await T('§17 安全风险', '风险大盘', 'GET', '/bidding/safety-risk/stats', { query: { entId: ENT_ID } });
  await T('§17 安全风险', '指派责任人', 'POST', '/bidding/safety-risk/assign', { body: { id: '1', assigneeId: '1', version: 0, remark: '[E2E]' } });
  await T('§17 安全风险', '处理风险', 'POST', '/bidding/safety-risk/handle', { body: { id: '1', targetStatus: 'HANDLED', version: 0, remark: '[E2E]' } });
  await T('§17 安全风险', '重新扫描风险', 'POST', '/bidding/safety-risk/scan', {});
  await T('§17 安全风险', '一键预警', 'POST', '/bidding/safety-risk/alert/batch', { body: { ids: ['1'], message: '[E2E]' } });
  await T('§17 安全风险', '导出风险报告', 'GET', '/bidding/safety-risk/export', { query: { pageNo: 1, pageSize: 10, entId: ENT_ID } });
  await T('§17 安全风险', '风险规则分页', 'GET', '/bidding/risk-rule/page', { query: { pageNo: 1, pageSize: 10, ruleName: 'E2E', enabled: true, riskLevel: 'HIGH' } });
  await T('§17 安全风险', '新增风险规则', 'POST', '/bidding/risk-rule/create', { body: { ruleName: '[E2E] 规则', condition: '> 30', conditionType: 'THRESHOLD', scope: 'CERT', ruleKey: 'e2e.rule', enabled: true, riskLevel: 'HIGH', remark: '[E2E]' } });
  await T('§17 安全风险', '修改风险规则', 'PUT', '/bidding/risk-rule/update', { body: { id: '1', version: 0, ruleName: '[E2E] 规则', condition: '> 60', conditionType: 'THRESHOLD', scope: 'CERT', ruleKey: 'e2e.rule', enabled: true, riskLevel: 'MEDIUM', remark: '[E2E]' } });
  await T('§17 安全风险', '启停风险规则', 'PUT', '/bidding/risk-rule/status', { query: { id: '1', enabled: false } });
  await T('§17 安全风险', '删除风险规则', 'DELETE', '/bidding/risk-rule/delete', { query: { id: '1' } });

  /* ---------- §18 安许风险联动 ---------- */
  await T('§18 安许风险', '读取安许风险状态', 'GET', '/bidding/safety-permit/risk', { query: { entId: ENT_ID, asOf: d(0) } });
  if (CTX.permitIds[0]) {
    await T('§18 安许风险', '重算安许风险（dryRun=false）', 'POST', '/bidding/safety-permit/risk/recheck', { query: { permitId: CTX.permitIds[0], asOf: d(0), dryRun: false } });
    await T('§18 安许风险', '重算安许风险（dryRun=true）', 'POST', '/bidding/safety-permit/risk/recheck', { query: { permitId: CTX.permitIds[0], asOf: d(0), dryRun: true } });
    await T('§18 安许风险', '风险快照分页', 'GET', '/bidding/safety-permit/risk/history', { query: { permitId: CTX.permitIds[0], pageNo: 1, pageSize: 10 } });
  } else {
    await T('§18 安许风险', '重算安许风险', 'POST', '/bidding/safety-permit/risk/recheck', { query: { permitId: 1, asOf: d(0), dryRun: false } }, '前置：无安许主键，占位 permitId');
    await T('§18 安许风险', '风险快照分页', 'GET', '/bidding/safety-permit/risk/history', { query: { permitId: 1, pageNo: 1, pageSize: 10 } }, '前置：无安许主键，占位 permitId');
  }
  await T('§18 安许风险', '登记整改宽限', 'POST', '/bidding/safety-permit/grace', {
    body: { permitId: CTX.permitIds[0] || 1, entId: ENT_ID, reason: 'LEGAL_PERSON_CHANGE', sourceRef: 'E2E-GRACE-REF', days: 30, remark: '[E2E] 宽限登记（reason 取自 safety.abc.grace.reason.whitelist）' },
  });
  await T('§18 安许风险', '多岗兼任校验', 'POST', '/bidding/safety-permit/personnel/multi-post-check', {
    body: { personId: P[0] || 1, primaryPost: 'PROJECT_MANAGER', secondaryPost: 'TECH_LEADER' },
  });
  await T('§18 安许风险', '人员风险', 'GET', '/bidding/safety-permit/personnel/risk', { query: { personId: P[0] || 1 } });
  await T('§18 安许风险', '项目关键岗位变更', 'GET', '/bidding/safety-permit/project/key-post-changes', { query: { projectId: 2099809111346696193, windowDays: 365 } });
  await T('§18 安许风险', '关键岗位任职/变更登记', 'POST', '/bidding/safety-permit/project/key-post-assignment', {
    body: { projectId: 2099809111346696193, postCode: 'PROJECT_MANAGER', personId: P[0] || 1, startAt: d(0), changeReason: '[E2E] 变更登记' },
  });
}


/* ============================ 9. 报告 ============================ */

const BT = String.fromCharCode(96); // markdown 反引号，避免污染本文件的模板字面量
const FENCE = BT + BT + BT;

function parseFieldErrors(raw) {
  try {
    const j = JSON.parse(raw);
    const fe = (j && j.data && j.data.fieldErrors) || (j && j.fieldErrors);
    if (Array.isArray(fe)) return fe.map((x) => x.field + '：' + (x.message || '')).join('；');
    if (j && j.msg) return String(j.msg);
  } catch (e) {
    /* ignore */
  }
  return '';
}
function classify(row) {
  if (row.ok) return '';
  const raw = row.raw || '';
  const isOfficial = String(row.path).indexOf('/ca/share/') >= 0;
  if (isOfficial) return '官方互认端点口径不同：以 {code:200,success:true} 表达成功，不套 CommonResult，非后端缺陷';
  if (row.code === 404) return '后端未实现（地址不存在，非脚本错误）';
  if (row.code === 403) return '权限承载缺口（403 无权限，需补权限码/菜单）';
  if (row.code === 401) return '未登录（token 失效）';
  if (row.code === 400) {
    const fe = parseFieldErrors(raw);
    return '参数校验失败 → 出错字段：' + (fe || '（后端未回 fieldErrors，仅 msg）');
  }
  if (row.code === null) return '非 CommonResult 响应（文件流或网络异常）：' + String(raw).slice(0, 160);
  if (Number(row.code) >= 1000000 && String(row.code).length === 10) return '后端业务错误 → ' + row.msg;
  return '业务失败 → ' + row.msg;
}
function esc(s) {
  return String(s === null || s === undefined ? '' : s).replace(/\|/g, '\\|').replace(/\r?\n/g, ' ');
}
function mdTable(headers, rows) {
  const out = ['| ' + headers.join(' | ') + ' |', '|' + headers.map(() => '---').join('|') + '|'];
  for (const r of rows) out.push('| ' + r.map(esc).join(' | ') + ' |');
  return out.join('\n');
}
function code(v) {
  return BT + v + BT;
}

function writeReport() {
  const total = R.length;
  const pass = R.filter((r) => r.ok).length;
  const officialOk = R.filter((r) => !r.ok && r.code === 200 && String(r.path).indexOf('/ca/share/') >= 0).length;
  const fail = total - pass;
  const realFail = fail - officialOk;
  const lines = [];
  lines.push('# 电子证照域 · 全量端到端测试与逐接口归因报告');
  lines.push('');
  lines.push('- 生成时间：' + nowStr() + '（' + today() + '）');
  lines.push('- 被测服务：' + code('http://127.0.0.1:48110') + '，上下文前缀 ' + code('/admin-api') + '，令牌走 ' + code('?token='));
  lines.push('- 登录通道：' + code('http://127.0.0.1:48080/admin-api/system/auth/login') + '（tenant-id: 1 / admin）');
  lines.push('- 数据基座：' + code('entId=' + ENT_ID) + '（' + HOLDER + '），默认租户 ' + code('1'));
  lines.push('- 判定口径：只看 ' + code('body.code') + '（HTTP 恒 200）；' + code('0') + '=成功、' + code('401') + '=未登录、' + code('403') + '=无权限、' + code('404') + '=地址不存在');
  lines.push('- 脚本：' + code('txgy-module-bidding/sql/tools/e2e-credential-full.js') + '（' + code('--clean') + ' / ' + code('--seed') + ' / 默认全跑）');
  lines.push('');
  lines.push('## 0. 结论概览');
  lines.push('');
  lines.push('- 接口调用次数：**' + total + '**（去重 ' + code('方法 + 路径') + ' ' + new Set(R.map((r) => r.method + ' ' + r.path)).size + ' 个）');
  lines.push('- ' + code('body.code=0') + ' 成功：**' + pass + '**');
  lines.push('- 失败：**' + realFail + '**' + (officialOk ? '（另有 ' + officialOk + ' 条官方互认端点以 ' + code('code=200,success=true') + ' 表达成功，不计失败）' : ''));
  lines.push('- 清理历史废数据：**' + CLEAN.actions.reduce((a, b) => a + b.count, 0) + '** 行');
  const seedDelta = Object.keys(CTX.countsAfter).reduce((a, k) => a + Math.max(0, (CTX.countsAfter[k] || 0) - (CTX.countsBefore[k] || 0)), 0);
  lines.push('- 新建数据：**' + seedDelta + '** 行（跨 ' + Object.keys(CTX.countsAfter).filter((k) => (CTX.countsAfter[k] || 0) - (CTX.countsBefore[k] || 0) > 0).length + ' 张表）');
  lines.push('');
  lines.push('## 1. 覆盖矩阵（接口 × 发送字段 × body.code × msg）');
  lines.push('');
  lines.push('> 「发送字段」= 本次请求真实发送的全部字段（含可选字段；' + code('(query)') + '=查询参数；' + code('(multipart)') + '=表单字段）。');
  lines.push('');
  const groups = [];
  for (const r of R) if (groups.indexOf(r.group) === -1) groups.push(r.group);
  for (const g of groups) {
    const rows = R.filter((r) => r.group === g);
    lines.push('### ' + g + '（' + rows.length + ' 次调用，成功 ' + rows.filter((r) => r.ok).length + '）');
    lines.push('');
    lines.push(mdTable(['接口', '方法 + 路径', '发送字段', 'HTTP', 'body.code', 'msg / 说明'], rows.map((r) => [
      r.name + (r.note ? '（' + r.note + '）' : '') + (r.ok ? '' : ' FAIL'),
      code(r.method + ' ' + r.path),
      r.sent.join(', '),
      r.status,
      r.code === null ? '(非 JSON)' : r.code,
      r.ok ? r.msg : '**' + r.msg + '**',
    ])));
    lines.push('');
  }

  lines.push('## 2. 清理清单（判据 + 行数 + 明细）');
  lines.push('');
  lines.push('### 2.1 判据（只删「明显坏数据 / 测试残留」，不碰看起来正常的历史数据）');
  lines.push('');
  lines.push(mdTable(['#', '表', '判据（WHERE）', '语义'], CLEAN.actions.map((a, i) => [i + 1, code(a.table), code(a.where), a.reason])));
  lines.push('');
  lines.push('### 2.2 各表清理前后行数');
  lines.push('');
  lines.push(mdTable(['表', '清理前', '清理后', '删除'], Object.keys(CLEAN.before).map((t) => [t, CLEAN.before[t], CLEAN.after[t], CLEAN.before[t] - CLEAN.after[t]])));
  lines.push('');
  lines.push('删除合计：**' + CLEAN.actions.reduce((a, b) => a + b.count, 0) + '** 行；涉及表 ' + new Set(CLEAN.actions.filter((a) => a.count > 0).map((a) => a.table)).size + ' 张。');
  lines.push('');
  /* 2.3 历史清理归档（journal 累计，含本轮之前的清理） */
  const journal = loadJournal();
  const jHist = journal.filter((j) => j.total > 0);
  lines.push('### 2.3 历史清理归档（累计 ' + journal.length + ' 次清理，其中 ' + jHist.length + ' 次有删除）');
  lines.push('');
  if (!jHist.length) {
    lines.push('（无历史清理记录）');
  } else {
    const agg = {};
    for (const j of jHist) {
      for (const a of j.actions) {
        if (!a.count) continue;
        const k = a.table + ' :: ' + a.reason;
        if (!agg[k]) agg[k] = { table: a.table, reason: a.reason, where: a.where, count: 0, runs: 0 };
        agg[k].count += a.count;
        agg[k].runs += 1;
      }
    }
    lines.push(mdTable(['表', '判据（WHERE）', '累计删除', '命中次数'], Object.keys(agg).map((k) => [code(agg[k].table), code(agg[k].where), agg[k].count, agg[k].runs])));
    lines.push('');
    lines.push('历次清理批次：');
    lines.push('');
    lines.push(mdTable(['批次时间', '删除行数', '涉及表'], jHist.map((j) => [j.at, j.total, Array.from(new Set(j.actions.filter((a) => a.count > 0).map((a) => a.table))).join(', ')])));
    lines.push('');
    for (const j of jHist) {
      for (const dt of j.detail || []) {
        if (!dt.rows || !dt.rows.length) continue;
        lines.push('**' + j.at + ' / ' + dt.title + '（' + dt.rows.length + ' 行）**');
        lines.push('');
        lines.push(FENCE);
        lines.push(dt.head);
        for (const rr of dt.rows) lines.push(rr);
        lines.push(FENCE);
        lines.push('');
      }
    }
  }
  lines.push('');
  for (const dt of CLEAN.detail) {
    lines.push('### ' + dt.title + '（' + dt.rows.length + ' 行）');
    lines.push('');
    if (!dt.rows.length) {
      lines.push('（无命中）');
    } else {
      lines.push(FENCE);
      lines.push(dt.head);
      for (const r of dt.rows) lines.push(r);
      lines.push(FENCE);
    }
    lines.push('');
  }

  lines.push('## 3. 新建数据清单（表 × 行数 × 关键字段样例）');
  lines.push('');
  const watch = ['certificate_info', 'enterprise_qualification', 'personnel_certificate', 'personnel_profile', 'cert_lifecycle_record', 'bidding_ca_seal', 'qualification_safety_permit', 'bidding_personnel_profile'];
  const deltaRows = Object.keys(CTX.countsAfter)
    .map((t) => [t, CTX.countsBefore[t], CTX.countsAfter[t], CTX.countsAfter[t] - CTX.countsBefore[t]])
    .filter((r) => r[3] !== 0 || watch.indexOf(r[0]) >= 0);
  lines.push(mdTable(['表', '造数前', '造数后', '新增'], deltaRows));
  lines.push('');
  lines.push('造数主键与文件快照：');
  lines.push('');
  lines.push(FENCE + 'json');
  lines.push(JSON.stringify({
    certificateIds: CTX.certIds,
    qualificationIds: CTX.qualIds,
    personnelProfileIds: CTX.personIds,
    personnelCertIds: CTX.personCertIds,
    safetyPermitIds: CTX.permitIds,
    lifecycleIds: CTX.lifecycleIds,
    renewApplyIds: CTX.applyIds,
    standardIds: CTX.standardIds,
    caSealDocumentIds: CTX.sealIds,
    uploadedFiles: Object.keys(CTX.files).reduce((a, k) => {
      a[k] = { fileId: CTX.files[k].fileId, name: CTX.files[k].name, size: CTX.files[k].size };
      return a;
    }, {}),
  }, null, 2));
  lines.push(FENCE);
  lines.push('');
  lines.push('造数分布（便于后续页面联表验证）：');
  lines.push('');
  lines.push(mdTable(['维度', '覆盖情况'], [
    ['证照类别', 'IDENTITY / QUALIFICATION / PERFORMANCE / CREDIT 四类齐备'],
    ['证照类型', 'ADMIN_LICENSE、SYSTEM_CERT、SAFETY_PERMIT、PERSON_QUAL、AUDIT_REPORT、PERFORMANCE、SOCIAL_SECURITY、HONOR、TAX_CREDIT、LEGAL_PERSON_ID、AUTH_LETTER'],
    ['有效期', '长期（2099-12-31）、临期（+3/+5/+10/+20/+45 天）、已过期（-30/-400 天）三档齐备'],
    ['状态', 'ACTIVE / SUSPENDED / REVOKED / CANCELLED（经 change-status 真实流转）'],
    ['持证主体', 'ORG（单位）与 PERSON（个人）两类'],
    ['资质', '序列 CONSTRUCTION/SURVEY/DESIGN/SUPERVISION/TESTING；等级 FIRST/SECOND/THIRD；标准 4 条 + 主档 12 条 + 人员配置 12 + 业绩 10 + 资产 8 + 核查 3'],
    ['安许', '4 条（临期/过期/长期）并做过状态流转'],
    ['员工档案', '10 名（学历/职称/部门/用工形式各异）'],
    ['员工证书', '12 张（6 种类型，临期与过期混合，全部挂真实上传 fileId）'],
    ['履历', '培训 10 / 社保 10 / 劳动合同 10'],
    ['生命周期', 'ANNUAL / CHANGE / RENEW / REVOKE 四类各一条 + 全字段合并提交一条，均带真佐证文件与真实新证文件'],
    ['CA 签章', '单文件 2 条 + 批量 2 条（真实 PDF/PNG 上传后签名落台账）'],
  ]));
  lines.push('');

  lines.push('## 4. 失败归因（接口 → 出错字段 → 原始响应 → 判断）');
  lines.push('');
  const fails = R.filter((r) => !r.ok);
  if (!fails.length) {
    lines.push('本次无失败项。');
  } else {
    lines.push('共 ' + fails.length + ' 条非 0 / 非成功返回，按归因类型汇总：');
    lines.push('');
    const buckets = {};
    for (const r of fails) {
      let kind;
      if (r.code === 404) kind = '后端未实现（404 地址不存在）';
      else if (r.code === 400) kind = '参数校验失败（400 / 请求参数不正确）';
      else if (r.code === 500) kind = '后端系统异常（code=500）';
      else if (r.code === 1060106039 || r.code === 1060107030 || r.code === 1060107025 || r.code === 1060107028 || r.code === 1060109005 || r.code === 1060109010 || r.code === 1060108000 || r.code === 1060100000) kind = '单据/字典/数据口径不匹配（需先满足前置数据）';
      else if (r.code === 1060109002 || r.code === 1060107017) kind = '乐观锁 version 冲突';
      else kind = '其他业务失败';
      if (!buckets[kind]) buckets[kind] = [];
      buckets[kind].push(r.method + ' ' + r.path);
    }
    lines.push(mdTable(['归因类型', '条数', '接口'], Object.keys(buckets).map((k) => [k, buckets[k].length, buckets[k].map((x) => code(x)).join('<br/>')])));
    lines.push('');
    lines.push('逐条明细：');
    lines.push('');
    for (let i = 0; i < fails.length; i++) {
      const r = fails[i];
      lines.push('### 4.' + (i + 1) + ' ' + code(r.method + ' ' + r.path) + '（' + r.name + '）');
      lines.push('');
      lines.push('- 发送字段：' + code(r.sent.join(', ')));
      lines.push('- 返回：' + code('HTTP ' + r.status + ' / body.code=' + r.code) + '，' + code('msg=' + r.msg));
      if (r.code === 400) lines.push('- **出错字段**：' + (parseFieldErrors(r.raw) || '（后端未回 fieldErrors）'));
      lines.push('- 原始响应：');
      lines.push(FENCE + 'json');
      lines.push(r.raw);
      lines.push(FENCE);
      lines.push('- 判断：' + classify(r));
      lines.push('');
    }
  }

  lines.push('## 5. 后端未实现接口清单（口径已定不做 / 待排期）');
  lines.push('');
  const missing = R.filter((r) => r.code === 404);
  if (missing.length) {
    lines.push(mdTable(['方法 + 路径', '前端入口', '当前返回'], missing.map((r) => [code(r.method + ' ' + r.path), r.name, 'code=404 ' + r.msg])));
  } else {
    lines.push('本次未命中 404。');
  }
  lines.push('');
  lines.push('> 口径说明：' + code('/bidding/subject-company/*') + ' 依《后端接口契约-资质证书五页改造》明确「本模块后端尚未实现」，记「后端未实现（口径已定不做）」，非脚本错误；' + code('/bidding/safety-risk/*') + '、' + code('/bidding/risk-rule/*') + ' 同为尚未落地的新增接口。');
  lines.push('');
  lines.push('## 6. 复现方式');
  lines.push('');
  lines.push(FENCE + 'bash');
  lines.push('cd <repo-root>');
  lines.push('node txgy-module-bidding/sql/tools/e2e-credential-full.js --clean   # 只清理，打印判据与前后行数');
  lines.push('node txgy-module-bidding/sql/tools/e2e-credential-full.js --seed    # 只造数（含真实 PDF/PNG/CSV 上传）');
  lines.push('node txgy-module-bidding/sql/tools/e2e-credential-full.js           # 清理 → 造数 → 全量调用 → 出本报告');
  lines.push(FENCE);
  lines.push('');
  lines.push('依赖：Node >= 18（内置 fetch/FormData/Blob）、本机可 ' + code('docker exec txgy-mysql') + '、bidding-server 已在 48110 监听。脚本绝不 kill/重启 Java 进程。');
  lines.push('');

  fs.mkdirSync(REPORT_DIR, { recursive: true });
  const file = path.join(REPORT_DIR, 'e2e-电子证照域全量-' + today() + '.md');
  fs.writeFileSync(file, lines.join('\n'), 'utf8');
  console.log('\n报告已写出：' + file);

  const covered = new Set(R.map((r) => r.method + ' ' + r.path));
  const missingFromRun = INVENTORY.filter((x) => !covered.has(x));
  if (missingFromRun.length) console.log('注意：清单中未覆盖的接口 → ' + missingFromRun.join(' , '));
  else console.log('覆盖自检通过：清单内 ' + INVENTORY.length + ' 个 (方法,路径) 全部至少真调一次。');
  return file;
}

/** 计划覆盖的 (方法, 路径) 清单 —— 用于覆盖自检 */
const INVENTORY = [
  'GET /bidding/meta/dicts', 'GET /bidding/meta/configs', 'GET /bidding/meta/options',
  'GET /bidding/certificate/page', 'GET /bidding/certificate/get', 'POST /bidding/certificate/create',
  'PUT /bidding/certificate/update', 'POST /bidding/certificate/change-status', 'GET /bidding/certificate/stats',
  'POST /bidding/certificate/file/register', 'GET /bidding/certificate/file/list',
  'POST /bidding/certificate/file/void', 'POST /bidding/certificate/file/correct',
  'POST /bidding/certificate-verify/verify', 'POST /bidding/certificate-verify/verify-file',
  'GET /bidding/certificate-verify/history/page', 'POST /bidding/certificate-verify/verify-batch',
  'POST /bidding/certificate-alert-run/scan', 'POST /bidding/certificate-alert-run/dispatch',
  'GET /bidding/certificate-alert-run/alert/page',
  'POST /bidding/cert-lifecycle/apply', 'POST /bidding/cert-lifecycle/audit', 'POST /bidding/cert-lifecycle/effect',
  'GET /bidding/cert-lifecycle/get', 'GET /bidding/cert-lifecycle/page', 'GET /bidding/cert-lifecycle/list-by-biz',
  'POST /bidding/cert-lifecycle/urge', 'GET /bidding/enterprise-material/completeness',
  'POST /bidding/certificate/import',
  'POST /bidding/file/upload', 'GET /bidding/file/download', 'POST /bidding/file/register-by-url',
  'GET /bidding/qualification/page', 'GET /bidding/qualification/get', 'POST /bidding/qualification/create',
  'PUT /bidding/qualification/update', 'POST /bidding/qualification/renew', 'POST /bidding/qualification/approve',
  'POST /bidding/qualification/reject', 'GET /bidding/qualification/apply/page',
  'GET /bidding/qualification/standard/page', 'GET /bidding/qualification/standard/options',
  'POST /bidding/qualification/standard/create', 'GET /bidding/qualification/element/staff/page',
  'GET /bidding/qualification/element/performance/page', 'GET /bidding/qualification/element/asset/page',
  'POST /bidding/qualification/element/staff', 'POST /bidding/qualification/element/performance',
  'POST /bidding/qualification/element/asset', 'GET /bidding/qualification/review/page',
  'POST /bidding/qualification/review/run', 'GET /bidding/qualification/permit/page',
  'GET /bidding/qualification/permit/get', 'POST /bidding/qualification/permit/create',
  'POST /bidding/qualification/permit/status', 'GET /bidding/qualification/alert/page',
  'POST /bidding/qualification/alert/handle', 'POST /bidding/qualification-alert-run/scan',
  'GET /bidding/qualification/stats', 'POST /bidding/qualification/import', 'GET /bidding/qualification/export-excel',
  'GET /bidding/personnel-certificate/page', 'GET /bidding/personnel-certificate/list-by-person',
  'GET /bidding/personnel-certificate/list-expiring', 'GET /bidding/personnel-certificate/count-by-sub-type',
  'GET /bidding/personnel-certificate/page-by-person', 'GET /bidding/personnel-certificate/stats',
  'POST /bidding/personnel-certificate/create', 'PUT /bidding/personnel-certificate/update',
  'PUT /bidding/personnel-certificate/change-status', 'DELETE /bidding/personnel-certificate/delete',
  'POST /bidding/personnel-certificate/batch-renew', 'POST /bidding/personnel-certificate/alert/notify',
  'POST /bidding/personnel-certificate/import', 'POST /bidding/personnel-certificate/verify',
  'POST /bidding/personnel-alert-run/scan', 'GET /bidding/personnel-alert-run/alert/page',
  'GET /bidding/personnel-alert-run/legal-floor-days',
  'GET /bidding/personnel-profile/page', 'GET /bidding/personnel-profile/get', 'GET /bidding/personnel-profile/overview',
  'POST /bidding/personnel-profile/create', 'PUT /bidding/personnel-profile/update',
  'PUT /bidding/personnel-profile/change-status', 'POST /bidding/personnel-record/training/create',
  'GET /bidding/personnel-record/training/list', 'POST /bidding/personnel-record/social-security/create',
  'GET /bidding/personnel-record/social-security/list', 'POST /bidding/personnel-record/labor-contract/create',
  'GET /bidding/personnel-record/labor-contract/list',
  'POST /bidding/ca/seal/document', 'POST /bidding/ca/seal/batch', 'GET /bidding/ca/seal/check',
  'GET /bidding/ca/seal/page', 'GET /bidding/ca/seal/stats', 'GET /bidding/ca/seal/get',
  'POST /bidding/ca/seal/verify', 'POST /bidding/ca/seal/callback', 'POST /bidding/ca/seal/demo-seed',
  'POST /bidding/ca/seal/demo-tamper', 'POST /bidding/ca/seal/demo-clear',
  'POST /bidding/ca/share/launch-param', 'POST /bidding/ca/share/seal/callback',
  'POST /bidding/ca/share/seal/client-callback', 'GET /bidding/ca/sm3', 'POST /bidding/ca/authenticate',
  'POST /bidding/ca/sign/qrcode', 'GET /bidding/ca/sign/status', 'POST /bidding/ca/sign/simulate',
  'GET /bidding/ca/sign/value', 'POST /bidding/ca/verify', 'POST /bidding/ca/login/qrcode',
  'GET /bidding/ca/login/status', 'GET /bidding/ca/login/auth-info', 'POST /bidding/ca/decrypt/qrcode',
  'GET /bidding/ca/decrypt/status', 'GET /bidding/ca/decrypt/user-info', 'GET /bidding/ca/decrypt/data',
  'GET /bidding/rule-config/get', 'POST /bidding/rule-config/save', 'POST /bidding/rule-config/rollback',
  'GET /bidding/rule-config/history', 'GET /bidding/rule-config/audit/page',
  'GET /bidding/subject/profile/get', 'POST /bidding/subject/profile/save',
  'GET /bidding/subject/profile/eligibility', 'GET /bidding/subject-auth/page',
  'POST /bidding/subject-auth/authorize', 'PUT /bidding/subject-auth/status',
  'GET /bidding/subject-auth/eligibility', 'GET /bidding/subject-auth/user/page',
  'POST /bidding/subject-auth/user/bind', 'DELETE /bidding/subject-auth/user/unbind',
  'GET /bidding/subject-company/page', 'GET /bidding/subject-company/get', 'POST /bidding/subject-company/create',
  'PUT /bidding/subject-company/update', 'PUT /bidding/subject-company/status', 'DELETE /bidding/subject-company/delete',
  'GET /bidding/safety-risk/page', 'GET /bidding/safety-risk/get', 'GET /bidding/safety-risk/stats',
  'POST /bidding/safety-risk/assign', 'POST /bidding/safety-risk/handle', 'POST /bidding/safety-risk/scan',
  'POST /bidding/safety-risk/alert/batch', 'GET /bidding/safety-risk/export',
  'GET /bidding/risk-rule/page', 'POST /bidding/risk-rule/create', 'PUT /bidding/risk-rule/update',
  'PUT /bidding/risk-rule/status', 'DELETE /bidding/risk-rule/delete',
  'GET /bidding/safety-permit/risk', 'POST /bidding/safety-permit/risk/recheck',
  'GET /bidding/safety-permit/risk/history', 'POST /bidding/safety-permit/grace',
  'POST /bidding/safety-permit/personnel/multi-post-check', 'GET /bidding/safety-permit/personnel/risk',
  'GET /bidding/safety-permit/project/key-post-changes', 'POST /bidding/safety-permit/project/key-post-assignment',
];

/* ============================ 10. 入口 ============================ */

(async () => {
  const t0 = Date.now();
  try {
    if (ONLY_CLEAN) {
      cleanUp();
      console.log('\n--clean 完成：仅清理，未造数、未调用接口。');
      return;
    }
    const info = await login();
    console.log('登录成功：userId=' + info.userId + '，token=' + String(TOKEN).slice(0, 8) + '...');
    if (!SKIP_CLEAN && !ONLY_SEED) cleanUp();
    await seedAll();
    if (ONLY_SEED) {
      console.log('\n--seed 完成：仅造数。');
      return;
    }
    await testAll();
    writeReport();
    console.log('\n全部完成，用时 ' + Math.round((Date.now() - t0) / 1000) + 's');
  } catch (e) {
    console.error('\n运行失败：' + (e && e.stack ? e.stack : e));
    process.exitCode = 1;
  }
})();



