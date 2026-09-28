#!/usr/bin/env node
/*
 * 外部公告 PDF「预览通道」体检器（只读，不写库、不改数据）
 *
 * 用途：为契约《后端变更请求-外部公告PDF预览平台归档读不出-2026-09-23》提供**可重复的验收证据**。
 *   修复前（2026-09-23 实测）：GX 源 347 条 PDF 公告，平台归档 345 条字节完好，
 *     但代理端点 346 条 404（归档分支被 SsrfGuard 拦：目标解析到受限地址 127.0.0.1；
 *     外站兜底被全局 5 分钟下载闸拦）→ 前端白屏。
 *   修复后期望：**凡归档字节可取到的公告，代理端点应全部 200 + application/pdf + %PDF 头**。
 *
 * 判定口径：
 *   ARCHIVE_* —— 直接 GET 公告行里存的 pdf_download_url（平台归档地址）的结果；
 *   PROXY_*   —— GET /admin-api/bidding/notice-external/pdf?id=（前端 iframe 唯一内联通道）的结果。
 *
 * 用法：
 *   node txgy-module-bidding/sql/tools/verify-notice-external-pdf-preview.cjs                 # 默认 GX 全量
 *   node ... --source=GX --limit=50 --concurrency=8
 *   node ... --json > report.json
 *   API_BASE=http://127.0.0.1:48080 MYSQL_CONTAINER=txgy-mysql DB=ruoyi-vue-pro node ...
 *
 * 退出码：0 = 归档可取的公告全部可通过代理预览（修复达成）；1 = 仍有缺口（附缺口清单）；2 = 环境不可用。
 */
const { execFileSync } = require('node:child_process');

const argv = process.argv.slice(2);
const opt = (name, def) => {
  const hit = argv.find((a) => a.startsWith('--' + name + '='));
  return hit ? hit.slice(name.length + 3) : def;
};
const has = (name) => argv.some((a) => a === '--' + name);
if (has('help')) {
  console.log('用法: node verify-notice-external-pdf-preview.cjs [--source=GX] [--limit=0] [--concurrency=8] [--json]');
  process.exit(0);
}
const API_BASE = process.env.API_BASE || 'http://127.0.0.1:48080';
const CONTAINER = process.env.MYSQL_CONTAINER || 'txgy-mysql';
const DB = process.env.DB || 'ruoyi-vue-pro';
const SOURCE = opt('source', 'GX');
const LIMIT = Number(opt('limit', '0')) || 0;
const CONC = Math.max(1, Number(opt('concurrency', '8')) || 8);
const AS_JSON = has('json');

function query(sql) {
  return execFileSync('docker', ['exec', '-i', CONTAINER, 'mysql', '--default-character-set=utf8mb4', '-uroot', '-p123456', '--raw', '-N', '-B', DB, '-e', sql],
    { encoding: 'utf8', maxBuffer: 1 << 26, stdio: ['ignore', 'pipe', 'ignore'] });
}

/** 取一条字节流并分类（不发二次请求、不判定业务态） */
async function probe(url) {
  try {
    const res = await fetch(url);
    const buf = Buffer.from(await res.arrayBuffer());
    const type = (res.headers.get('content-type') || '').split(';')[0].trim();
    const magic = buf.subarray(0, 5).toString('latin1');
    if (res.status === 200 && magic.startsWith('%PDF')) return { cls: 'OK_PDF', http: 200, type, len: buf.length };
    if (res.status === 200 && type === 'application/json') return { cls: 'JSON_200', http: 200, type, len: buf.length };
    if (res.status === 200 && buf.length === 0) return { cls: 'EMPTY_200', http: 200, type, len: 0 };
    if (res.status === 200) return { cls: 'NOT_PDF_200', http: 200, type, len: buf.length };
    return { cls: 'HTTP_' + res.status, http: res.status, type, len: buf.length };
  } catch (e) {
    return { cls: 'FETCH_ERR', http: 0, type: '', len: 0, err: String(e.message || e).slice(0, 80) };
  }
}

(async () => {
  let rows;
  try {
    const sql = "SELECT id, IFNULL(pdf_file_id, ''), IFNULL(pdf_size, 0), status, IFNULL(pdf_download_url, '')"
      + " FROM bidding_notice_external WHERE deleted = 0 AND content_type = 'PDF'"
      + (SOURCE ? " AND source_code = '" + SOURCE + "'" : '')
      + ' ORDER BY id' + (LIMIT ? ' LIMIT ' + LIMIT : '');
    rows = query(sql).split('\n').filter(Boolean).map((l) => l.split('\t'));
  } catch (e) {
    console.error('环境不可用（读库失败）：' + String(e.message || e).split('\n')[0]);
    console.error('提示：确认容器名/库名，或用 MYSQL_CONTAINER= / DB= / API_BASE= 覆盖。');
    process.exit(2);
  }

  const out = [];
  let cursor = 0;
  async function worker() {
    while (cursor < rows.length) {
      const [id, fileId, size, status, url] = rows[cursor++];
      const archive = url ? await probe(url) : { cls: 'NO_URL', http: 0, type: '', len: 0 };
      const proxy = await probe(API_BASE + '/admin-api/bidding/notice-external/pdf?id=' + id);
      out.push({ id, fileId, size: Number(size), status, archive: archive.cls, proxy: proxy.cls, proxyLen: proxy.len });
    }
  }
  await Promise.all(Array.from({ length: CONC }, worker));

  const tally = (key) => out.reduce((m, r) => ((m[r[key]] = (m[r[key]] || 0) + 1), m), {});
  const archiveOk = out.filter((r) => r.archive === 'OK_PDF');
  const gap = archiveOk.filter((r) => r.proxy !== 'OK_PDF');       // 归档可取但代理不通 = 缺陷
  const lost = out.filter((r) => r.archive === 'NO_URL');          // 真无归档（需回填，非本缺陷）

  if (AS_JSON) {
    console.log(JSON.stringify({
      apiBase: API_BASE, source: SOURCE, total: out.length,
      archive: tally('archive'), proxy: tally('proxy'),
      archiveOkButProxyBroken: gap.length, noArchive: lost.length, rows: out,
    }, null, 2));
  } else {
    console.log('[体检] source=' + (SOURCE || 'ALL') + ' 公告=' + out.length + ' api=' + API_BASE);
    console.log('  归档地址直取 : ' + JSON.stringify(tally('archive')));
    console.log('  代理端点     : ' + JSON.stringify(tally('proxy')));
    console.log('  结论：归档可取但代理不通 = ' + gap.length + ' 条；真无归档 = ' + lost.length + ' 条');
    if (gap.length) {
      console.log('  缺口清单（前 20）：' + gap.slice(0, 20).map((r) => r.id + '(' + r.proxy + ')').join(', '));
    }
    if (lost.length) {
      console.log('  真无归档（建议 POST /bidding/notice-external/backfill-pdf-urls 逐条回填）：' + lost.map((r) => r.id).join(', '));
    }
    console.log(gap.length === 0
      ? '  ✅ 通过：归档可取的公告全部能通过代理内联预览'
      : '  ❌ 未通过：仍有 ' + gap.length + ' 条归档可取却预览 404（契约《外部公告PDF预览平台归档读不出-2026-09-23》待修）');
  }
  process.exit(gap.length === 0 ? 0 : 1);
})();
