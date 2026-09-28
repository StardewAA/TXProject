#!/usr/bin/env node
/* eslint-disable no-console */
/**
 * 电子证照业务全流程 E2E 造数脚本（企业租户：延河热力集团有限公司 / tenant 2019 / yanhereli）
 * ---------------------------------------------------------------------------
 * 链路：授权 → 主体档案 → 企业证书×20（建档 + 真上传 + 文件版本登记） → 员工档案 + 员工证书×50
 *
 * 用法：
 *   node txgy-module-bidding/sql/tools/e2e-cert-flow-yanhereli.js            # 默认全跑：先清自己的残留，再种数、再校验
 *   node txgy-module-bidding/sql/tools/e2e-cert-flow-yanhereli.js --seed     # 只种数（幂等，按业务键跳过已存在）
 *   node txgy-module-bidding/sql/tools/e2e-cert-flow-yanhereli.js --clean    # 只清理本脚本造的数据（删前打印判据与行数）
 *
 * 幂等：所有本脚本写入的行都带统一前缀 —— 备注/名称 [E2E-YH]、证书号 E2E-YH-CERT-*、员工号 E2E-YH-P-*、
 *       员工证书号 E2E-YH-PC-*；二次执行按业务键复用，不重复堆。
 * 约束：不修改后端 Java；不重启服务。写接口之间 sleep 1.35s（>1.3s），规避 @Idempotent 重复请求窗口。
 *
 * 产出：
 *   .tmp/e2e-yh/result.json   完整逐接口记录 + 文件清单 + SQL 校验计数（报告由它生成）
 *   .tmp/e2e-yh/files/*       真实下载（或本地生成）的样例文件
 *
 * @since 2026-09-22
 */
'use strict';

const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

// ============================== 配置 ==============================
const ROOT = path.resolve(__dirname, '..', '..', '..');
const OUT_DIR = path.join(ROOT, '.tmp', 'e2e-yh');
const FILE_DIR = path.join(OUT_DIR, 'files');
const GATEWAY = 'http://127.0.0.1:48080/admin-api';
const BACKEND = 'http://127.0.0.1:48110/admin-api';

const ENT = { username: 'yanhereli', password: 'admin123', tenant: '2019', tenantName: '延河热力集团有限公司' };
const ADMIN = { username: 'admin', password: 'admin123', tenant: '1' };

const P = '[E2E-YH]';                 // 统一备注前缀
const CERT_PREFIX = 'E2E-YH-CERT-';   // 企业证书号前缀
const PERSON_PREFIX = 'E2E-YH-P-';    // 员工工号前缀
const PCERT_PREFIX = 'E2E-YH-PC-';    // 员工证书号前缀

const TODAY = new Date(2026, 8, 22); // 环境当前日期 2026-09-22（避免容器/宿主时区漂移）
const SLEEP_MS = 1350;

// 外网样例文件（可公开访问）
const SRC = {
  pdf: 'https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf',
  png: 'https://dummyimage.com/800x600/000/fff.png',
  jpg: 'https://dummyimage.com/600x400/fff/000.jpg',
  jpeg: 'https://picsum.photos/800/600',
};

// ============================== 基础工具 ==============================
function sleep(ms) { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); }

function sh(cmd, args, opts) {
  const r = spawnSync(cmd, args, Object.assign({ encoding: 'utf8', maxBuffer: 1 << 26 }, opts || {}));
  return { stdout: (r.stdout || '').trim(), stderr: (r.stderr || '').trim(), status: r.status };
}

function db(sql) {
  const r = sh('docker', ['exec', '-i', 'txgy-mysql', 'mysql', '--default-character-set=utf8mb4',
    '-uroot', '-p123456', 'ruoyi-vue-pro', '-N', '-B'], { input: sql });
  if (/ERROR \d+/.test(r.stderr)) throw new Error('SQL 失败: ' + r.stderr + '\nSQL: ' + sql);
  return r.stdout;
}
function dbRows(sql) { return db(sql).split('\n').map((l) => l.trim()).filter(Boolean).map((l) => l.split('\t')); }
function dbOne(sql) { const r = dbRows(sql); return r.length ? r[0] : null; }
function dbScalar(sql) { const r = dbOne(sql); return r ? r[0] : null; }

function curl(args) {
  const r = sh('curl', ['-s', '--max-time', '90'].concat(args));
  return r.stdout;
}

/**
 * 雪花 ID 是 19 位十进制，超过 JS Number.MAX_SAFE_INTEGER(2^53≈9e15)，
 * 直接 JSON.parse 会被静默改写（写回库里就是另一个 id）。
 * 这里把 >=16 位的整数字面量加引号后再解析 —— 字段本身仍是数字语义，回传时后端 Jackson 会把字符串强转 Long。
 */
function bigIntSafe(text) {
  return text.replace(/:\s*(-?\d{16,})(?=\s*[,}\]])/g, ':"$1"');
}
/** 统一把 id 序列化成 JSON 文本可用的形式（字符串；超长为雪花 id，短长为普通 id） */
function sid(v) { return v === null || v === undefined ? v : String(v); }

// ============================== HTTP ==============================
const journal = [];
let currentStep = '-';

function record(apiName, fields, code, msg, note) {
  journal.push({ step: currentStep, api: apiName, fields: fields, code: code, msg: msg || '', note: note || '' });
}

let ENT_TOKEN = null;
let ADMIN_TOKEN = null;

function login(acc) {
  const body = JSON.stringify({ username: acc.username, password: acc.password });
  const fp = path.join(OUT_DIR, 'login.json');
  fs.writeFileSync(fp, body);
  const out = curl(['-X', 'POST', GATEWAY + '/system/auth/login',
    '-H', 'Content-Type: application/json', '-H', 'tenant-id: ' + acc.tenant,
    '--data-binary', '@' + fp]);
  const j = JSON.parse(bigIntSafe(out));
  return j && j.data ? j.data.accessToken : null;
}

/** 统一调用后端（直连 48110，上下文 /admin-api，token 走查询参数） */
function api(method, p, opts) {
  opts = opts || {};
  const token = opts.token || ENT_TOKEN;
  const tenant = opts.tenant || (opts.token === ADMIN_TOKEN ? ADMIN.tenant : ENT.tenant);
  const url = BACKEND + p + (p.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token);
  const args = ['-X', method, '-H', 'tenant-id: ' + tenant, '-H', 'Authorization: Bearer ' + token];
  if (opts.json !== undefined) {
    const fp = path.join(OUT_DIR, 'body.json');
    fs.writeFileSync(fp, JSON.stringify(opts.json));
    args.push('-H', 'Content-Type: application/json', '--data-binary', '@' + fp);
  }
  if (opts.multipart) {
    for (const f of opts.multipart) args.push('-F', f);
  }
  args.push(url);
  const raw = curl(args);
  let body;
  try { body = JSON.parse(bigIntSafe(raw)); } catch (e) { body = { code: -1, msg: 'NON_JSON: ' + raw.slice(0, 300), data: null }; }
  // 只在写接口之间 sleep（约束：写接口 @Idempotent 去重窗口 ≥1.3s）；读接口不 sleep 以缩短重跑时间
  if (opts.sleep !== false && method !== 'GET') sleep(SLEEP_MS);
  if (opts.record !== false) {
    record(method + ' ' + p.split('?')[0], opts.fields || opts.json || opts.multipart || {}, body.code, body.msg, opts.note);
  }
  return body;
}

// ============================== 编码校验工具 ==============================
const USCC_CHARSET = '0123456789ABCDEFGHJKLMNPQRTUWXY';
const USCC_W = [1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28];
/** GB 32100-2015：补齐第 18 位校验码 */
function usccFix(prefix17) {
  let sum = 0;
  for (let i = 0; i < 17; i++) sum += USCC_CHARSET.indexOf(prefix17[i]) * USCC_W[i];
  return prefix17 + USCC_CHARSET[(31 - (sum % 31)) % 31];
}
/** GB 11643：补齐身份证第 18 位校验码 */
function idCardFix(prefix17) {
  const w = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const c = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];
  let s = 0;
  for (let i = 0; i < 17; i++) s += Number(prefix17[i]) * w[i];
  return prefix17 + c[s % 11];
}

function ymd(d) {
  const p2 = (n) => String(n).padStart(2, '0');
  return d.getFullYear() + '-' + p2(d.getMonth() + 1) + '-' + p2(d.getDate());
}
function plusDays(n) { const d = new Date(TODAY.getTime()); d.setDate(d.getDate() + n); return ymd(d); }

// ============================== 本地生成合法文件（外网兜底） ==============================
function genPdf(text) {
  const objs = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 420 200] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>\nendobj\n',
    null,
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n',
  ];
  const stream = 'BT /F1 14 Tf 30 120 Td (' + text.replace(/[()\\]/g, '') + ') Tj ET\n';
  objs[3] = '4 0 obj\n<< /Length ' + Buffer.byteLength(stream) + ' >>\nstream\n' + stream + 'endstream\nendobj\n';
  let out = '%PDF-1.4\n';
  const offsets = [];
  for (const o of objs) { offsets.push(Buffer.byteLength(out)); out += o; }
  const xref = Buffer.byteLength(out);
  out += 'xref\n0 ' + (objs.length + 1) + '\n0000000000 65535 f \n';
  for (const off of offsets) out += String(off).padStart(10, '0') + ' 00000 n \n';
  out += 'trailer\n<< /Size ' + (objs.length + 1) + ' /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF\n';
  return Buffer.from(out, 'latin1');
}

const CRC_TABLE = (function () {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; t[n] = c; }
  return t;
})();
function crc32(buf) { let c = -1; for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8); return (c ^ -1) >>> 0; }
function pngChunk(type, data) {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length, 0);
  const td = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(td), 0);
  return Buffer.concat([len, td, crc]);
}
function genPng(w, h, rgb) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0); ihdr.writeUInt32BE(h, 4);
  ihdr[8] = 8; ihdr[9] = 2; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const raw = Buffer.alloc((w * 3 + 1) * h);
  for (let y = 0; y < h; y++) {
    const off = y * (w * 3 + 1); raw[off] = 0;
    for (let x = 0; x < w; x++) { raw[off + 1 + x * 3] = rgb[0]; raw[off + 2 + x * 3] = rgb[1]; raw[off + 3 + x * 3] = rgb[2]; }
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk('IHDR', ihdr), pngChunk('IDAT', zlib.deflateSync(raw)), pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

// ============================== 文件准备 ==============================
const fileManifest = {};
const fallbackFiles = [];

function prepareFiles(names) {
  // 1) 逐个外网下载一次（按扩展名复用缓存）
  for (const ext of ['pdf', 'png', 'jpg']) {
    const cache = path.join(FILE_DIR, '_src.' + ext);
    if (fs.existsSync(cache) && fs.statSync(cache).size > 0) continue;
    const r = sh('curl', ['-sL', '--max-time', '40', '-o', cache, SRC[ext]]);
    const ok = fs.existsSync(cache) && fs.statSync(cache).size > 200;
    if (!ok || r.status !== 0) {
      fallbackFiles.push(ext + '（外网下载失败，改用本地生成）');
      fs.writeFileSync(cache, ext === 'pdf' ? genPdf(String.fromCharCode(...[]) + 'E2E offline pdf') : genPng(120, 80, [10, 60, 120]));
    }
  }
  // 2) 按目标文件名落盘（内容取自对应扩展名的缓存）
  for (const n of names) {
    const ext = path.extname(n).slice(1).toLowerCase();
    const src = path.join(FILE_DIR, '_src.' + (ext === 'jpeg' ? 'jpg' : ext));
    const dst = path.join(FILE_DIR, n);
    fs.copyFileSync(src, dst);
    fileManifest[n] = {
      localPath: dst,
      bytes: fs.statSync(dst).size,
      ext: ext,
      mime: ext === 'pdf' ? 'application/pdf' : ext === 'png' ? 'image/png' : 'image/jpeg',
      source: fallbackFiles.some((f) => f.indexOf(ext) === 0) ? '本地生成' : SRC[ext === 'jpeg' ? 'jpeg' : ext],
      fileId: null, sha256: null,
    };
  }
}

function upload(fileName) {
  const m = fileManifest[fileName];
  const resp = api('POST', '/bidding/file/upload', {
    multipart: ['file=@' + m.localPath + ';filename=' + fileName + ';type=' + m.mime],
    fields: { file: fileName + ' (' + m.bytes + 'B, ' + m.mime + ')' },
    note: '真上传：' + fileName,
  });
  if (resp.code === 0 && resp.data) {
    m.fileId = resp.data.fileId;
    m.sha256 = resp.data.sha256;
    m.uploadName = resp.data.name;
    m.uploadSize = resp.data.size;
    m.uploadType = resp.data.type;
    m.url = resp.data.url;
  } else {
    m.uploadError = resp.code + ' ' + resp.msg;
  }
  return resp;
}

// ============================== 计划数据 ==============================
const PLANS_ENT = [
  { no: 1, certName: '营业执照', certType: 'ADMIN_LICENSE', cat: 'IDENTITY', ext: 'pdf', level: '正本', scope: '城市集中供热、热力工程施工与运营', auth: '西安市市场监督管理局', authCode: '91610113MA0E2YH01', valid: 'LONG' },
  { no: 2, certName: '法定代表人身份证明', certType: 'LEGAL_PERSON_ID', cat: 'IDENTITY', ext: 'pdf', level: '—', scope: '法定代表人身份证明', auth: '西安市公安局', authCode: '91610113MA0E2YH01', valid: 720 },
  { no: 3, certName: '法人授权委托书', certType: 'AUTH_LETTER', cat: 'IDENTITY', ext: 'pdf', level: '—', scope: '授权办理招投标事宜', auth: '延河热力集团有限公司', authCode: '91610113MA0E2YH01', valid: 2 },
  { no: 4, certName: '质量管理体系认证证书 ISO9001', certType: 'SYSTEM_CERT', cat: 'QUALIFICATION', ext: 'png', level: '—', scope: '热力工程施工与运行维护', auth: '中鉴认证有限责任公司', authCode: '91110000100000001X', valid: 'LONG' },
  { no: 5, certName: '环境管理体系认证证书 ISO14001', certType: 'SYSTEM_CERT', cat: 'QUALIFICATION', ext: 'jpg', level: '—', scope: '热力工程施工与运行维护', auth: '中鉴认证有限责任公司', authCode: '91110000100000001X', valid: 900 },
  { no: 6, certName: '安全生产许可证', certType: 'SAFETY_PERMIT', cat: 'QUALIFICATION', ext: 'png', level: '—', scope: '建筑施工', auth: '陕西省住房和城乡建设厅', authCode: '11610000016000003K', valid: 12 },
  { no: 7, certName: '特种设备作业人员证', certType: 'PERSON_QUAL', cat: 'QUALIFICATION', ext: 'pdf', level: 'G2', scope: '压力管道巡检维护', auth: '陕西省市场监督管理局', authCode: '11610000016000003K', valid: -45 },
  { no: 8, certName: '建筑业企业资质证书', certType: 'SYSTEM_CERT', cat: 'QUALIFICATION', ext: 'pdf', level: '市政公用工程施工总承包二级', scope: '市政公用工程施工', auth: '陕西省住房和城乡建设厅', authCode: '11610000016000003K', valid: 1100 },
  { no: 9, certName: '供热管网改造工程业绩证明', certType: 'PERFORMANCE', cat: 'PERFORMANCE', ext: 'pdf', level: '—', scope: '供热管网改造工程业绩', auth: '西安市热力总公司', authCode: '91610100200000002Y', valid: 'LONG' },
  { no: 10, certName: '2024年度财务审计报告', certType: 'AUDIT_REPORT', cat: 'CREDIT', ext: 'pdf', level: '—', scope: '2024年度财务报表审计', auth: '陕西正衡会计师事务所', authCode: '91610000200000003Z', valid: -300 },
  { no: 11, certName: '纳税信用等级证明', certType: 'TAX_CREDIT', cat: 'CREDIT', ext: 'jpg', level: 'A级', scope: '纳税信用等级', auth: '国家税务总局西安市税务局', authCode: '11610000016000003K', valid: -120 },
  { no: 12, certName: '社会保险缴纳证明', certType: 'SOCIAL_SECURITY', cat: 'CREDIT', ext: 'png', level: '—', scope: '近三个月社保缴纳', auth: '西安市人力资源和社会保障局', authCode: '11610000016000003K', valid: 20 },
  { no: 13, certName: '省级文明工地荣誉证书', certType: 'HONOR', cat: 'CREDIT', ext: 'png', level: '省级', scope: '文明工地', auth: '陕西省住房和城乡建设厅', authCode: '11610000016000003K', valid: 'LONG' },
  { no: 14, certName: '城市集中供热经营许可证', certType: 'SYSTEM_CERT', cat: 'QUALIFICATION', ext: 'pdf', level: '—', scope: '城市集中供热经营', auth: '西安市城市管理和综合执法局', authCode: '11610000016000003K', valid: 'LONG' },
  { no: 15, certName: '起重设备安装工程专业承包资质', certType: 'SYSTEM_CERT', cat: 'QUALIFICATION', ext: 'pdf', level: '三级', scope: '起重设备安装工程', auth: '陕西省住房和城乡建设厅', authCode: '11610000016000003K', valid: 600 },
  { no: 16, certName: '安全生产考核合格证（B类）', certType: 'PERSON_QUAL', cat: 'QUALIFICATION', ext: 'png', level: 'B类', scope: '项目负责人安全生产考核', auth: '陕西省住房和城乡建设厅', authCode: '11610000016000003K', valid: 5 },
  { no: 17, certName: '压力管道安装许可证 GB1', certType: 'SAFETY_PERMIT', cat: 'QUALIFICATION', ext: 'pdf', level: 'GB1', scope: '压力管道安装', auth: '陕西省市场监督管理局', authCode: '11610000016000003K', valid: -10 },
  { no: 18, certName: '信息化建设业绩证明', certType: 'PERFORMANCE', cat: 'PERFORMANCE', ext: 'jpg', level: '—', scope: '热网监控信息化建设业绩', auth: '延河热力集团有限公司', authCode: '91610113MA0E2YH01', valid: 500 },
  { no: 19, certName: '银行资信证明', certType: 'AUDIT_REPORT', cat: 'CREDIT', ext: 'pdf', level: 'AAA', scope: '银行资信', auth: '中国银行西安分行', authCode: '91610000200000004A', valid: 400 },
  { no: 20, certName: '特种设备使用登记证', certType: 'PERSON_QUAL', cat: 'QUALIFICATION', ext: 'png', level: '—', scope: '压力容器使用登记', auth: '陕西省市场监督管理局', authCode: '11610000016000003K', valid: 28 },
];

// 建档后要做的状态流转（多样状态）
const ENT_STATUS = {
  6: 'EXPIRING_SOON', 7: 'EXPIRED', 11: 'EXPIRED', 13: 'SUSPENDED',
  15: 'REVOKED', 16: 'EXPIRING_SOON', 18: 'CANCELLED',
};

const PEOPLE = [
  { idx: 1, name: '张建国', post: '项目经理', dept: '工程管理部' },
  { idx: 2, name: '李红梅', post: '技术负责人', dept: '技术部' },
  { idx: 3, name: '王振华', post: '专职安全员', dept: '安全监察部' },
  { idx: 4, name: '赵晓峰', post: '质量负责人', dept: '质量管理部' },
  { idx: 5, name: '陈丽君', post: '资料员', dept: '综合办公室' },
  { idx: 6, name: '刘志强', post: '施工员', dept: '工程管理部' },
];
const PERSON_SPLIT = [9, 9, 9, 9, 7, 7]; // 合计 50

const PCERT_TYPES = [
  { t: 'ID_CARD', n: '身份证', sub: null, lvl: null, edu: null },
  { t: 'EDUCATION_CERT', n: '学历证书', sub: null, lvl: null, edu: 'BACHELOR' },
  { t: 'DEGREE_CERT', n: '学位证书', sub: null, lvl: null, edu: 'BACHELOR' },
  { t: 'TITLE_CERT', n: '工程师职称证书', sub: null, lvl: '中级', edu: null },
  { t: 'REG_PRACTICE_CERT', n: '一级建造师注册证书', sub: null, lvl: '一级', edu: null },
  { t: 'SAFETY_ASSESS_CERT', n: '安全生产考核合格证', sub: 'B', lvl: 'B类', edu: null },
  { t: 'SKILL_CERT', n: '职业技能等级证书', sub: null, lvl: '高级工', edu: null },
  { t: 'TRAINING_CERT', n: '培训合格证书', sub: null, lvl: null, edu: null },
  { t: 'OTHER', n: '特种作业操作证', sub: null, lvl: null, edu: null },
];
// 注意：EXPIRING_SOON 不在可手工流转的目标态内（后端迁移表拒绝，1060107007）——该状态应由预警扫描派生。
const PCERT_STATUS = { 2: 'EXPIRED', 7: 'EXPIRED', 12: 'EXPIRED', 20: 'REVOKED', 30: 'SUSPENDED' };

function planPersonnelCerts() {
  const plans = [];
  let n = 0;
  for (let pi = 0; pi < PEOPLE.length; pi++) {
    for (let k = 0; k < PERSON_SPLIT[pi]; k++) {
      n += 1;
      const ty = PCERT_TYPES[(n - 1) % PCERT_TYPES.length];
      const ext = ['pdf', 'png', 'jpg'][(n - 1) % 3];
      // 后端口径：身份证/执业资格/职称/安全考核/技能类**必须**登记有效期（1060107009）；
      // 只有学历/学位/培训/其他允许长期（validTo=null）。故"长期"档按类型分流：允许的留空，不允许的给远期有效。
      const NO_EXPIRY_REQUIRED = { EDUCATION_CERT: 1, DEGREE_CERT: 1, TRAINING_CERT: 1, OTHER: 1 };
      let valid;
      switch ((n - 1) % 5) {
        case 0: valid = NO_EXPIRY_REQUIRED[ty.t] ? 'LONG' : 20000; break;
        case 1: valid = 10 + (n % 15); break;
        case 2: valid = -(20 + (n % 40)); break;
        case 3: valid = 300 + (n % 300); break;
        default: valid = 700 + (n % 400); break;
      }
      plans.push({
        no: n, person: PEOPLE[pi], type: ty, ext: ext, valid: valid,
        certNo: PCERT_PREFIX + String(n).padStart(3, '0'),
        fileName: PEOPLE[pi].name + '-' + ty.n + '.' + ext,
      });
    }
  }
  return plans;
}

// ============================== 清理 ==============================
function clean() {
  console.log('===== --clean：清理本脚本造的数据（判据：统一前缀 ' + P + ' / ' + CERT_PREFIX + ' / ' + PERSON_PREFIX + ' / ' + PCERT_PREFIX + '） =====');
  const entIds = db("SELECT id FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%'")
    .split('\n').map((s) => s.trim()).filter(Boolean);
  const personIds = db("SELECT id FROM bidding_personnel_profile WHERE person_no LIKE '" + PERSON_PREFIX + "%'")
    .split('\n').map((s) => s.trim()).filter(Boolean);
  const inEnt = entIds.length ? entIds.join(',') : '0';
  const inPer = personIds.length ? personIds.join(',') : '0';

  const report = [];
  const table = [];
  const q = (label, sql) => { const n = dbScalar('SELECT COUNT(*) FROM (' + sql + ') t'); table.push(label + '=' + n); };
  q('certificate_info', "SELECT id FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%'");
  q('cert_file_versions', 'SELECT id FROM cert_file_versions WHERE cert_id IN (' + inEnt + ')');
  q('cert_change_logs', 'SELECT id FROM cert_change_logs WHERE cert_id IN (' + inEnt + ')');
  q('personnel_certificate', "SELECT id FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%'");
  q('bidding_personnel_profile', "SELECT id FROM bidding_personnel_profile WHERE person_no LIKE '" + PERSON_PREFIX + "%'");
  q('bidding_subject_auth(我的)', "SELECT id FROM bidding_subject_auth WHERE remark LIKE '" + P + "%'");
  q('bidding_subject_profile(我的)', "SELECT id FROM bidding_subject_profile WHERE remark LIKE '" + P + "%'");
  console.log('判据命中行数：' + table.join('  '));
  const safeDel = (label, sql) => {
    try {
      // 只把 DELETE FROM 换成 SELECT 1 FROM，保留 WHERE 判据，保证"删前打印判据与行数"是真的判据行数
      const n = dbScalar('SELECT COUNT(*) FROM (' + sql.replace(/^DELETE\s+FROM\s+/i, 'SELECT 1 FROM ') + ') t');
      db(sql);
      console.log('  删除 ' + label + '：WHERE 命中 ' + n + ' 行');
    } catch (e) { console.log('  跳过 ' + label + '：' + String(e.message).split('\n')[0]); }
  };
  if (entIds.length) {
    safeDel('cert_file_versions', 'DELETE FROM cert_file_versions WHERE cert_id IN (' + inEnt + ')');
    safeDel('cert_change_logs', 'DELETE FROM cert_change_logs WHERE cert_id IN (' + inEnt + ')');
    safeDel('cert_verify_history', 'DELETE FROM cert_verify_history WHERE cert_id IN (' + inEnt + ')');
    safeDel('cert_alert_records', 'DELETE FROM cert_alert_records WHERE cert_id IN (' + inEnt + ')');
    safeDel('cert_lifecycle_record', 'DELETE FROM cert_lifecycle_record WHERE biz_id IN (' + inEnt + ')');
    safeDel('cert_notify_outbox', 'DELETE FROM cert_notify_outbox WHERE biz_id IN (' + inEnt + ')');
    safeDel('bidding_review_task(CERTIFICATE)', "DELETE FROM bidding_review_task WHERE biz_type='CERTIFICATE' AND biz_id IN (" + inEnt + ')');
  }
  const pcIds = db("SELECT id FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%'")
    .split('\n').map((s) => s.trim()).filter(Boolean);
  // 上传文件主键：必须在本脚本的业务行被删掉之前采集（bidding_file.name 是存储名，不是业务文件名，不能按名匹配）
  const myFileIds = (entIds.length ? db('SELECT file_id FROM cert_file_versions WHERE cert_id IN (' + inEnt + ')') : '')
    .split('\n').concat(db("SELECT file_id FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%' AND file_id IS NOT NULL").split('\n'))
    .map((s) => s.trim()).filter((s) => /^\d+$/.test(s));
  const inFile = myFileIds.length ? myFileIds.join(',') : '0';
  const inPc = pcIds.length ? pcIds.join(',') : '0';
  if (pcIds.length) safeDel('bidding_review_task(PERSON_CERT)', "DELETE FROM bidding_review_task WHERE biz_type='PERSON_CERT' AND biz_id IN (" + inPc + ')');
  safeDel('personnel_certificate', "DELETE FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%'");
  if (entIds.length) safeDel('certificate_info', 'DELETE FROM certificate_info WHERE id IN (' + inEnt + ')');
  if (personIds.length) {
    safeDel('personnel_training_record', 'DELETE FROM personnel_training_record WHERE person_id IN (' + inPer + ')');
    safeDel('personnel_social_security', 'DELETE FROM personnel_social_security WHERE person_id IN (' + inPer + ')');
    safeDel('personnel_labor_contract', 'DELETE FROM personnel_labor_contract WHERE person_id IN (' + inPer + ')');
    safeDel('bidding_personnel_profile', 'DELETE FROM bidding_personnel_profile WHERE id IN (' + inPer + ')');
  }
  safeDel('bidding_subject_auth(我的)', "DELETE FROM bidding_subject_auth WHERE remark LIKE '" + P + "%'");
  safeDel('bidding_subject_profile(我的)', "DELETE FROM bidding_subject_profile WHERE remark LIKE '" + P + "%'");
  // 上传文件记录：按本脚本采集到的 file_id 精确删除（bidding_file.name 是"时间戳-序号-原名"的存储名）
  if (myFileIds.length) {
    safeDel('bidding_file(本脚本 file_id)', 'DELETE FROM bidding_file WHERE tenant_id=2019 AND file_id IN (' + inFile + ')');
  } else {
    console.log('  bidding_file：无本脚本 file_id，跳过');
  }
  console.log('清理完成。');
  return report;
}

// ============================== 主流程 ==============================
function doSeed() {
  // ---------- 0. 登录 ----------
  currentStep = '0-登录';
  ENT_TOKEN = login(ENT); ADMIN_TOKEN = login(ADMIN);
  record('POST /system/auth/login', { username: ENT.username, tenant: ENT.tenant }, ENT_TOKEN ? 0 : -1, ENT_TOKEN ? '' : '登录失败', '企业账号');
  record('POST /system/auth/login', { username: ADMIN.username, tenant: ADMIN.tenant }, ADMIN_TOKEN ? 0 : -1, ADMIN_TOKEN ? '' : '登录失败', '平台账号');
  if (!ENT_TOKEN || !ADMIN_TOKEN) throw new Error('登录失败，终止');

  // ---------- 1. 授权 ----------
  currentStep = '1-授权（平台侧 bidding:subject-auth:update）';
  api('POST', '/bidding/subject-auth/authorize', {
    token: ADMIN_TOKEN, tenant: ADMIN.tenant, sleep: true,
    json: { tenantId: 1, tenantName: '西安鸥鹏互联科技有限公司', subjectType: 'BIDDER', remark: P + ' 授权探针A：admin 为自身租户重复授权' },
    note: '探针A：admin 在自身租户内重复授权（应在服务层被判重复，用于证明接口在租户内可用）',
  });
  api('POST', '/bidding/subject-auth/authorize', {
    token: ADMIN_TOKEN, tenant: ADMIN.tenant,
    json: { tenantId: 2019, tenantName: ENT.tenantName, subjectType: 'BIDDER', remark: P + ' 授权探针B：admin 跨租户授权 2019' },
    note: '探针B：平台账号为租户 2019 授权（跨租户）',
  });
  api('POST', '/bidding/subject-auth/authorize', {
    token: ENT_TOKEN, tenant: ENT.tenant,
    json: { tenantId: 2019, tenantName: ENT.tenantName, subjectType: 'BIDDER', remark: P + ' 授权探针C：企业账号为自己重复授权' },
    note: '探针C：企业账号调用平台侧授权接口（验证是否 403）',
  });
  const probeD = api('POST', '/bidding/subject-auth/authorize', {
    token: ENT_TOKEN, tenant: ENT.tenant,
    json: { tenantId: 2019, tenantName: ENT.tenantName, subjectType: 'TENDERER', remark: P + ' 授权探针D：企业账号给自己新增 TENDERER 授权' },
    note: '探针D：企业账号是否真的能写入授权（越权写验证）',
  });
  const authRows = dbRows("SELECT id, tenant_id, subject_type, auth_status, tenant_name, authorize_user_id, remark FROM bidding_subject_auth ORDER BY id");
  const probeDRow = dbRows("SELECT id FROM bidding_subject_auth WHERE remark LIKE '" + P + "%' AND tenant_id=2019 AND subject_type='TENDERER'");
  let cleanedProbe = 0;
  if (probeDRow.length) {
    cleanedProbe = probeDRow.length;
    db("DELETE FROM bidding_subject_auth WHERE remark LIKE '" + P + "%'");
    console.log('[授权探针D] 写入成功 → 已删除本次探针行 ' + cleanedProbe + ' 行（避免留下越权写入的授权）');
  }
  const authTable = {
    allRows: authRows,
    probeDWriteSucceeded: probeD.code === 0,
    probeDCleaned: cleanedProbe,
  };

  // ---------- 2. 主体档案 ----------
  currentStep = '2-主体档案（GET/POST /bidding/subject/profile）';
  const before = api('GET', '/bidding/subject/profile/get', { fields: { subjectType: '(省略)' }, note: '看现状（未建档返回 null）' });
  const baseProfile = {
    subjectType: 'BIDDER',
    subjectName: ENT.tenantName,
    uscc: USCC_VALID,
    legalPersonName: '王延河',
    legalPersonTypeCode: '1',
    regionCode: '610113',
    industryCode: 'D44',
    regCapital: 50000000,
    regCapCurrency: 'CNY',
    contactName: '赵敏',
    contactPhone: '13900000000',
    extJson: JSON.stringify({ legalPersonIdNo: '610113198001011234', regCapCurrencyName: 'CNY 人民币' }),
    remark: P + ' 主体档案端到端造数（USCC 为校验位合法的合成值，备注见报告）',
  };
  // 2.1 故意用前端可能传的 ISO 币种字母码，复现缺陷
  const badCur = api('POST', '/bidding/subject/profile/save', { json: baseProfile, note: '缺陷复现：regCapCurrency=CNY（前端 ISO 字母码）' });
  // 2.2 换合法值继续
  const okProfile = Object.assign({}, baseProfile, { regCapCurrency: '156' });
  const save = api('POST', '/bidding/subject/profile/save', { json: okProfile, note: '合法币种数字码（GB/T 12406 156=人民币）' });
  if (save.code !== 0 && /重复请求/.test(save.msg || '')) {
    api('POST', '/bidding/subject/profile/save', { json: okProfile, sleep: true, record: false, note: '幂等窗口重试' });
  }
  const after = api('GET', '/bidding/subject/profile/get', { record: false });
  const profileRow = dbRows('SELECT id, tenant_id, subject_type, subject_name, uscc, legal_person_name, legal_person_type_code, region_code, industry_code, reg_capital, reg_cap_currency, contact_name, contact_phone, complete_flag, license_file_id FROM bidding_subject_profile WHERE tenant_id=2019');
  const ENT_ID = profileRow.length ? profileRow[0][0] : (after.data && after.data.id);
  const eligibility = api('GET', '/bidding/subject/profile/eligibility', { record: false });
  journal.push({ step: currentStep, api: 'GET /bidding/subject/profile/eligibility', fields: {}, code: eligibility.code, msg: eligibility.msg, note: '资格状态：' + JSON.stringify(eligibility.data) });

  // ---------- 3. 企业证书 ×20 ----------
  currentStep = '3-企业证书×20（建档 + 真上传 + 文件版本登记）';
  prepareFiles(PLANS_ENT.map((p) => p.certName + '.' + p.ext).concat(['营业执照.pdf']));
  // 3.0 主体档案的营业执照影像（走同一条上传链路）
  const lic = upload('营业执照.pdf');
  if (lic.code === 0) {
    api('POST', '/bidding/subject/profile/save', {
      json: Object.assign({}, okProfile, { licenseFileId: lic.data.fileId, licenseFileName: '营业执照.pdf' }),
      note: '回填营业执照 fileId 到主体档案',
    });
  }
  const entCerts = [];
  for (const pl of PLANS_ENT) {
    const certNo = CERT_PREFIX + String(pl.no).padStart(2, '0');
    const fileName = pl.certName + '.' + pl.ext;
    const found = api('GET', '/bidding/certificate/page?pageNo=1&pageSize=5&certNo=' + certNo, { record: false });
    let certId = found.code === 0 && found.data && found.data.list && found.data.list.length ? found.data.list[0].id : null;
    if (!certId) {
      const payload = {
        certName: pl.certName, certNo: certNo, certType: pl.certType, certCategoryCode: pl.cat,
        certLevel: pl.level, certScope: pl.scope, holderName: ENT.tenantName, holderType: 'ORG',
        holderId: ENT_ID ? sid(ENT_ID) : undefined, issuingAuthority: pl.auth, issuingAuthorityCode: pl.authCode,
        issueDate: plusDays(-900), validFrom: plusDays(-1000),
        validTo: pl.valid === 'LONG' ? undefined : plusDays(pl.valid), remark: P + ' 企业证书端到端造数 #' + pl.no,
      };
      const r = api('POST', '/bidding/certificate/create', { json: payload, note: fileName });
      if (r.code === 0) certId = r.data;
      else if (/重复请求/.test(r.msg || '')) { sleep(SLEEP_MS); const r2 = api('POST', '/bidding/certificate/create', { json: payload, record: false }); certId = r2.data; }
    } else {
      journal.push({ step: currentStep, api: 'GET /bidding/certificate/page', fields: { certNo: certNo }, code: 0, msg: '', note: '幂等命中，复用 certId=' + certId });
    }
    if (!certId) { entCerts.push({ plan: pl, certId: null, err: 'create 失败' }); continue; }
    // 文件版本：已登记则跳过
    const fl = api('GET', '/bidding/certificate/file/list?certId=' + certId, { record: false });
    if (!(fl.code === 0 && Array.isArray(fl.data) && fl.data.length)) {
      const up = upload(fileName);
      if (up.code === 0 && up.data) {
        api('POST', '/bidding/certificate/file/register', {
          json: {
            certId: certId, fileCategory: 'ORIGINAL', fileName: fileName, fileId: up.data.fileId,
            fileHash: up.data.sha256, fileFormat: pl.ext.toUpperCase(), fileSize: up.data.size,
            remark: P + ' 文件版本登记',
          },
          note: fileName + ' → certId=' + certId,
        });
      }
    } else {
      journal.push({ step: currentStep, api: 'GET /bidding/certificate/file/list', fields: { certId: certId }, code: 0, msg: '', note: '幂等命中，已有文件版本' });
    }
    entCerts.push({ plan: pl, certId: certId, certNo: certNo, fileName: fileName });
  }
  // 状态流转
  for (const no of Object.keys(ENT_STATUS)) {
    const c = entCerts.find((x) => x.plan.no === Number(no));
    if (!c || !c.certId) continue;
    const g = api('GET', '/bidding/certificate/get?id=' + c.certId, { record: false });
    const ver = g.data ? g.data.version : null;
    if (ver === null || ver === undefined) continue;
    if (g.data.certStatus === ENT_STATUS[no]) { journal.push({ step: currentStep, api: 'GET /bidding/certificate/get', fields: { id: c.certId }, code: 0, msg: '', note: '幂等命中，已是 ' + ENT_STATUS[no] }); continue; }
    api('POST', '/bidding/certificate/change-status', {
      json: { certificateId: sid(c.certId), targetStatus: ENT_STATUS[no], version: ver, reason: P + ' 状态流转覆盖' },
      note: c.plan.certName + ' → ' + ENT_STATUS[no],
    });
  }

  // ---------- 4. 员工档案 + 员工证书 ×50 ----------
  currentStep = '4-员工档案（/bidding/personnel-profile）';
  const people = [];
  for (const p of PEOPLE) {
    const personNo = PERSON_PREFIX + String(p.idx).padStart(2, '0');
    const found = api('GET', '/bidding/personnel-profile/page?pageNo=1&pageSize=5&personNo=' + personNo, { record: false });
    let id = found.code === 0 && found.data && found.data.list && found.data.list.length ? found.data.list[0].id : null;
    if (!id) {
      const payload = {
        entId: sid(ENT_ID), personNo: personNo, personName: p.name,
        idCard: idCardFix('6101131980' + String(p.idx).padStart(2, '0') + '01' + String(100 + p.idx)),
        idCardValidTo: plusDays(2000), gender: p.idx % 2 === 1 ? 'MALE' : 'FEMALE',
        birthDate: '1980-0' + ((p.idx % 9) + 1) + '-01', mobile: '138000000' + String(p.idx).padStart(2, '0'),
        email: 'e2e-yh-' + p.idx + '@yanhereli.example.com', eduLevel: 'BACHELOR', eduMajor: '热能与动力工程',
        school: '西安建筑科技大学', graduateDate: '2003-07-01', titleLevel: '中级', titleMajor: '暖通',
        workStartDate: '2003-08-01', joinDate: '2015-03-01', deptName: p.dept, positionName: p.post,
        employmentType: 'FULL_TIME', archiveNo: 'DA-' + String(p.idx).padStart(4, '0'),
        remark: P + ' 员工档案端到端造数 ' + p.name,
      };
      const r = api('POST', '/bidding/personnel-profile/create', { json: payload, note: p.name + ' 建档' });
      if (r.code === 0) id = r.data;
      else if (/重复请求/.test(r.msg || '')) { sleep(SLEEP_MS); const r2 = api('POST', '/bidding/personnel-profile/create', { json: payload, record: false }); id = r2.data; }
    } else {
      journal.push({ step: currentStep, api: 'GET /bidding/personnel-profile/page', fields: { personNo: personNo }, code: 0, msg: '', note: '幂等命中，复用 personId=' + id });
    }
    people.push({ def: p, id: id, personNo: personNo });
  }

  currentStep = '5-员工证书×50（/bidding/personnel-certificate）';
  const pcPlans = planPersonnelCerts();
  prepareFiles(pcPlans.map((x) => x.fileName));
  const pcerts = [];
  for (const pl of pcPlans) {
    const person = people[pl.person.idx - 1];
    if (!person || !person.id) continue;
    const found = api('GET', '/bidding/personnel-certificate/page?pageNo=1&pageSize=5&personId=' + person.id + '&certNo=' + pl.certNo, { record: false });
    let certId = found.code === 0 && found.data && found.data.list && found.data.list.length ? found.data.list[0].id : null;
    if (!certId) {
      const payload = {
        entId: sid(ENT_ID), personId: sid(person.id), certType: pl.type.t, certName: pl.type.n,
        certNo: pl.certNo, certLevel: pl.type.lvl, certSubType: pl.type.sub,
        nextReviewDate: pl.valid === 'LONG' ? undefined : plusDays(pl.valid > 0 ? Math.max(pl.valid - 60, 1) : -60),
        major: '供热通风与空调工程', eduLevel: pl.type.edu, issuingAuthority: pl.type.t === 'ID_CARD' ? '西安市公安局' : '陕西省住房和城乡建设厅',
        issueDate: plusDays(-800), validFrom: plusDays(-900),
        validTo: pl.valid === 'LONG' ? undefined : plusDays(pl.valid),
        registerUnit: pl.type.t === 'REG_PRACTICE_CERT' ? ENT.tenantName : undefined,
        registerUnitCode: pl.type.t === 'REG_PRACTICE_CERT' ? USCC_VALID : undefined,
        registerNo: pl.type.t === 'REG_PRACTICE_CERT' ? 'SX-' + pl.no : undefined,
        platformVerified: false, extraProps: JSON.stringify({ source: 'E2E-YH' }),
        remark: P + ' 员工证书端到端造数 #' + pl.no,
      };
      const r = api('POST', '/bidding/personnel-certificate/create', { json: payload, note: pl.person.name + '-' + pl.type.n });
      if (r.code === 0) certId = r.data;
      else if (/重复请求/.test(r.msg || '')) { sleep(SLEEP_MS); const r2 = api('POST', '/bidding/personnel-certificate/create', { json: payload, record: false }); certId = r2.data; }
    } else {
      journal.push({ step: currentStep, api: 'GET /bidding/personnel-certificate/page', fields: { certNo: pl.certNo }, code: 0, msg: '', note: '幂等命中，复用 certId=' + certId });
    }
    if (!certId) { pcerts.push({ plan: pl, certId: null }); continue; }
    // 文件：先上传，再把 fileId 回填到证书（员工证书无独立文件版本表，fileId 直接挂在证书行）
    const g = api('GET', '/bidding/personnel-certificate/page?pageNo=1&pageSize=5&personId=' + person.id + '&certNo=' + pl.certNo, { record: false });
    const row = g.code === 0 && g.data && g.data.list && g.data.list.length ? g.data.list[0] : null;
    if (row && !row.fileId) {
      const up = upload(pl.fileName);
      if (up.code === 0 && up.data) {
        api('PUT', '/bidding/personnel-certificate/update', {
          json: {
            id: sid(certId), entId: sid(ENT_ID), personId: sid(person.id), certType: pl.type.t,
            certName: pl.type.n, certNo: pl.certNo, certLevel: pl.type.lvl, certSubType: pl.type.sub,
            fileId: up.data.fileId, remark: P + ' 员工证书端到端造数 #' + pl.no, version: row.version,
          },
          note: pl.fileName + ' fileId=' + up.data.fileId + ' → certId=' + certId,
        });
      }
    } else {
      journal.push({ step: currentStep, api: 'GET /bidding/personnel-certificate/page', fields: { certNo: pl.certNo }, code: 0, msg: '', note: '幂等命中，已有 fileId=' + (row ? row.fileId : '') });
    }
    pcerts.push({ plan: pl, certId: certId, personId: person.id });
  }
  // 员工证书状态流转
  for (const no of Object.keys(PCERT_STATUS)) {
    const c = pcerts.find((x) => x.plan.no === Number(no));
    if (!c || !c.certId) continue;
    const g = api('GET', '/bidding/personnel-certificate/page?pageNo=1&pageSize=5&personId=' + c.personId + '&certNo=' + c.plan.certNo, { record: false });
    const row = g.data && g.data.list && g.data.list.length ? g.data.list[0] : null;
    if (!row) continue;
    if (row.certStatus === PCERT_STATUS[no]) { journal.push({ step: currentStep, api: 'GET /bidding/personnel-certificate/page', fields: { certNo: c.plan.certNo }, code: 0, msg: '', note: '幂等命中，已是 ' + PCERT_STATUS[no] }); continue; }
    api('PUT', '/bidding/personnel-certificate/change-status', {
      json: { id: sid(c.certId), targetStatus: PCERT_STATUS[no], version: row.version, reason: P + ' 状态流转覆盖' },
      note: c.plan.certNo + ' → ' + PCERT_STATUS[no],
    });
  }

  // ---------- 6. 数据库实测校验 ----------
  currentStep = '6-数据库实测校验';
  const checks = {
    subjectAuth: dbRows('SELECT id, tenant_id, subject_type, auth_status, tenant_name, authorize_user_id, remark FROM bidding_subject_auth ORDER BY id'),
    subjectProfile: dbRows('SELECT id, tenant_id, subject_type, subject_name, uscc, legal_person_name, region_code, industry_code, reg_capital, reg_cap_currency, contact_name, complete_flag, license_file_id, license_file_name FROM bidding_subject_profile ORDER BY id'),
    entCertCount: dbScalar("SELECT COUNT(*) FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%'"),
    entCertByCat: dbRows("SELECT cert_category_code, cert_status, COUNT(*) FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%' GROUP BY cert_category_code, cert_status ORDER BY cert_category_code"),
    entCertValidStates: dbRows("SELECT CASE WHEN valid_to IS NULL THEN 'LONG_TERM' WHEN valid_to < CURDATE() THEN 'EXPIRED' WHEN valid_to <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN 'EXPIRING' ELSE 'NORMAL' END s, COUNT(*) FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%' GROUP BY s"),
    entFileVersions: dbScalar('SELECT COUNT(*) FROM cert_file_versions v JOIN certificate_info c ON c.id=v.cert_id WHERE c.cert_no LIKE \'' + CERT_PREFIX + '%\''),
    entFileVirus: dbRows('SELECT v.virus_status, COUNT(*) FROM cert_file_versions v JOIN certificate_info c ON c.id=v.cert_id WHERE c.cert_no LIKE \'' + CERT_PREFIX + '%\' GROUP BY v.virus_status'),
    personnelProfiles: dbScalar("SELECT COUNT(*) FROM bidding_personnel_profile WHERE person_no LIKE '" + PERSON_PREFIX + "%'"),
    personnelCertCount: dbScalar("SELECT COUNT(*) FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%'"),
    personnelCertByType: dbRows("SELECT cert_type, cert_status, COUNT(*) FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%' GROUP BY cert_type, cert_status ORDER BY cert_type"),
    personnelCertValidStates: dbRows("SELECT CASE WHEN valid_to IS NULL THEN 'LONG_TERM' WHEN valid_to < CURDATE() THEN 'EXPIRED' WHEN valid_to <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) THEN 'EXPIRING' ELSE 'NORMAL' END s, COUNT(*) FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%' GROUP BY s"),
    personnelCertWithFile: dbScalar("SELECT COUNT(*) FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%' AND file_id IS NOT NULL"),
    samplesEntCert: dbRows("SELECT id, cert_no, cert_name, cert_type, cert_category_code, cert_status, valid_to, holder_name FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%' ORDER BY cert_no LIMIT 5"),
    samplesPcert: dbRows("SELECT id, person_id, cert_type, cert_name, cert_status, valid_to, file_id FROM personnel_certificate WHERE cert_no LIKE '" + PCERT_PREFIX + "%' ORDER BY cert_no LIMIT 5"),
    sampleFileVersions: dbRows('SELECT v.id, v.cert_id, v.file_category, v.file_name, v.file_id, v.file_format, v.file_size, v.virus_status, v.is_current, v.version FROM cert_file_versions v JOIN certificate_info c ON c.id=v.cert_id WHERE c.cert_no LIKE \'' + CERT_PREFIX + '%\' ORDER BY v.id LIMIT 5'),
    uploadedFileIds: dbScalar("SELECT COUNT(DISTINCT file_id) FROM bidding_file WHERE tenant_id=2019 AND name IN (SELECT file_name FROM cert_file_versions WHERE cert_id IN (SELECT id FROM certificate_info WHERE cert_no LIKE '" + CERT_PREFIX + "%'))"),
  };
  return { ENT_ID: ENT_ID, authTable: authTable, profileBefore: before, profileBadCurrency: badCur, profileSave: save, profileAfter: after, checks: checks, entCerts: entCerts, pcerts: pcerts, people: people };
}

const USCC_VALID = usccFix('91610113MA0E2YH01');

// ============================== main ==============================
function main() {
  const argv = process.argv.slice(2);
  const doCleanFlag = argv.indexOf('--clean') >= 0;
  const doSeedFlag = argv.indexOf('--seed') >= 0;
  const explicit = doCleanFlag || doSeedFlag;

  fs.mkdirSync(FILE_DIR, { recursive: true });

  let seedResult = null;
  const t0 = Date.now();
  const mode = doCleanFlag && !doSeedFlag ? 'clean' : (doSeedFlag && !doCleanFlag ? 'seed' : 'full');
  console.log('运行模式：' + mode + (mode === 'full' ? '（先清理自己的残留，再全量种数）' : ''));
  if (mode === 'full') clean();
  if (mode === 'clean') { clean(); console.log('仅清理模式完成。'); return; }
  seedResult = doSeed();

  // 落盘结果
  const result = {
    generatedAt: new Date().toISOString(),
    config: { tenant: ENT.tenant, tenantName: ENT.tenantName, user: ENT.username, prefix: P },
    fallbackFiles: fallbackFiles,
    files: fileManifest,
    journal: journal,
    summary: seedResult ? {
      ENT_ID: seedResult.ENT_ID,
      authTableRows: seedResult.authTable.allRows,
      probeDWriteSucceeded: seedResult.authTable.probeDWriteSucceeded,
      probeDCleaned: seedResult.authTable.probeDCleaned,
      checks: seedResult.checks,
    } : null,
    counts: {
      entPlanned: PLANS_ENT.length,
      personnelCertPlanned: planPersonnelCerts().length,
      filesPlanned: Object.keys(fileManifest).length,
      apiCalls: journal.length,
      writes5xx: journal.filter((x) => x.code === 500).length,
      failures: journal.filter((x) => x.code !== 0 && x.code !== undefined).length,
    },
  };
  fs.writeFileSync(path.join(OUT_DIR, 'result.json'), JSON.stringify(result, null, 1));
  console.log('耗时 ' + Math.round((Date.now() - t0) / 1000) + 's；接口调用 ' + journal.length + ' 次；结果写入 ' + path.join(OUT_DIR, 'result.json'));
  console.log('失败/异常接口 ' + result.counts.failures + ' 条（其中 500 = ' + result.counts.writes5xx + '）');
  for (const j of journal.filter((x) => x.code !== 0)) {
    console.log('  [' + j.code + '] ' + j.api + ' :: ' + (j.msg || '').slice(0, 80) + ' :: ' + j.note);
  }
  if (seedResult) {
    console.log('=== SQL 计数 ===');
    console.log('企业证书(2019,PREFIX) = ' + seedResult.checks.entCertCount);
    console.log('企业证书文件版本   = ' + seedResult.checks.entFileVersions);
    console.log('员工档案           = ' + seedResult.checks.personnelProfiles);
    console.log('员工证书           = ' + seedResult.checks.personnelCertCount + '（带 fileId ' + seedResult.checks.personnelCertWithFile + '）');
    console.log('staff valid states = ' + JSON.stringify(seedResult.checks.personnelCertValidStates));
  }
}

main();
