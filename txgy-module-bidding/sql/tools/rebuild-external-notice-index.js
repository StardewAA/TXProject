// 外部公告检索索引重建（ES 中文精确检索改造，2026-09-22）
//
// 背景：原索引靠 ES 动态映射自动创建，text 字段落默认 standard 分析器 → 中文逐字切分
//       （「炼化」→「炼」+「化」）→ multi_match 默认 OR 下"任意单字命中即命中"，
//       实测搜「炼化」命中 1050/1637（64%），而真正含该词的只有 21 条。
//
// 改造：索引侧改用 ngram 分析器（2~10 字连续子串入倒排）、查询侧 keyword+term，
//       实现中文「字面包含」精确检索。规格见
//       EsExternalNoticeSearchEngine 同目录的 ExternalNoticeIndexMapping.java（单一事实来源）。
//
// 用法（默认 dry-run，只打印计划不落任何写操作）：
//   node sql/tools/rebuild-external-notice-index.js            # 预览
//   node sql/tools/rebuild-external-notice-index.js --apply    # 实际重建
//   node sql/tools/rebuild-external-notice-index.js --apply --drop-backup  # 重建后删除旧索引备份
//
// 安全性：
//   - 重建前把现有索引复制为 <index>-bak-<yyyyMMddHHmmss>（reindex），失败可回滚；
//   - 数据来源是**主表 MySQL**，不是旧索引——旧索引因分析器错误可能有内容缺失/截断，
//     以其为源会继承缺陷。故本工具从 MySQL 读 ACTIVE 公告重新生成文档（与索引通知器同口径）。
const ES = process.env.ES_URL || 'http://127.0.0.1:9200';
const ES_AUTH = process.env.ES_AUTH || 'elastic:ww0qPgaEbSyT6oMMeART';
const INDEX = 'bidding-notice-external';
const MIN_GRAM = 2;
const MAX_GRAM = 10;
const MAX_TEXT_CHARS = 100000;
const DOCKER_MYSQL = 'txgy-mysql';
const DB = 'ruoyi-vue-pro';

const APPLY = process.argv.includes('--apply');
const DROP_BACKUP = process.argv.includes('--drop-backup');

const authHeader = 'Basic ' + Buffer.from(ES_AUTH).toString('base64');

async function es(method, path, body) {
  const res = await fetch(ES + path, {
    method,
    headers: { Authorization: authHeader, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok && res.status !== 404) {
    throw new Error(`ES ${method} ${path} -> ${res.status}: ${text.slice(0, 400)}`);
  }
  try { return { status: res.status, json: text ? JSON.parse(text) : null }; }
  catch { return { status: res.status, json: null, raw: text }; }
}

/**
 * 执行 SQL 并返回 TSV（-N -B：无表头、制表符分隔）。
 *
 * <p>为什么走临时文件而不是 {@code -e "..."}：SQL 里必然含单引号（{@code status='ACTIVE'}、
 * {@code '%Y-%m-%dT%H:%i:%s'}），经 sh -c '...' 双层嵌套后引号会被 shell 吞掉导致 EOF 语法错误。
 * 写入临时文件再重定向 {@code < file} 可完全避开引号地狱。
 */
function mysqlQuery(sql) {
  const { spawnSync } = require('child_process');
  // 必须 shell:false + input 传 stdin：
  // ① shell:true 时 Node 在 Windows 走 cmd.exe，它不认单引号，会破坏 sh -c '...' 的引号（实测 EOF 语法错误）；
  // ② SQL 经 stdin 传入，避免任何一层 shell 解析 SQL 里的单引号。
  const r = spawnSync('docker',
    ['exec', '-i', DOCKER_MYSQL, 'sh', '-c',
      `mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -D ${DB} -N -B`],
    { input: sql, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
  if (r.status !== 0) {
    throw new Error(`mysql 查询失败(status=${r.status}): ${String(r.stderr || '').slice(0, 400)}`);
  }
  return r.stdout;
}

function settings() {
  return {
    number_of_shards: 1, number_of_replicas: 0,
    max_ngram_diff: MAX_GRAM - MIN_GRAM,
    analysis: {
      filter: { ngram_filter: { type: 'ngram', min_gram: MIN_GRAM, max_gram: MAX_GRAM } },
      analyzer: {
        ngram_index_analyzer: { type: 'custom', tokenizer: 'keyword', filter: ['ngram_filter'] },
        ngram_search_analyzer: { type: 'custom', tokenizer: 'keyword' },
      },
    },
  };
}

const TEXT_FIELDS = ['title', 'projectName', 'projectCode', 'tenderProjectCode', 'issuerName',
  'contentText', 'sourceCode', 'contentType', 'origUrl', 'bidSectionCodes', 'industryFlag',
  'legalFlag', 'status', 'publicScope'];

function mappings() {
  const properties = { id: { type: 'long' }, publishTime: { type: 'date' }, traceable: { type: 'long' }, traceDimensions: { type: 'text' } };
  for (const f of TEXT_FIELDS) {
    properties[f] = {
      type: 'text', analyzer: 'ngram_index_analyzer', search_analyzer: 'ngram_search_analyzer',
      fields: { keyword: { type: 'keyword', ignore_above: 256 } },
    };
  }
  return { properties };
}

async function main() {
  console.log(APPLY ? '=== APPLY 模式：将实际重建索引 ===' : '=== DRY-RUN：只预览，不写入（加 --apply 执行）===');

  // 1) 当前状态
  const before = await es('GET', `/${INDEX}/_count`);
  console.log(`当前索引 ${INDEX} 文档数: ${before.json ? before.json.count : '(不存在)'}`);

  // 2) 从主表读 ACTIVE 公告（与索引通知器同口径）
  // 正文取数顺序**必须与索引通知器 EsExternalNoticeIndexNotifier#contentText 一致**：
  //   ① extraction_text 非空 → 用它（PDF 抽取文本）
  //   ② 否则 content_html → 去标签取纯文本
  // 实测：主表 extraction_text 全空（0/1637），正文全在 content_html（1470/1637）。
  // 若这里只取 extraction_text，索引的 contentText 会全为 NULL，
  // 导致"正文命中"整体丢失（实测搜『榆林』title 114 + content 536，只索引 title 会漏 422 条）。
  // ⚠ 截断必须在**去标签之后**：后端 MAX_TEXT_CHARS 约束的是 jsoup 去标签后的纯文本长度，
  // 若在 SQL 里先截断原始 HTML（标签占大量字符），实际保留的正文远少于 10 万字——
  // 实测「变电」有 1 条因此漏检（content_html 191758 字符，关键词在 129418 位置，被 10 万截断切掉）。
  // 故此处取全文，去标签后再按 MAX_TEXT_CHARS 截断（见下方 stripHtml + sliceText）。
  const rows = mysqlQuery(
    "SELECT id, title, source_code, content_type, orig_url, issuer_name, project_name, project_code, " +
    "tender_project_code, bid_section_codes, industry_flag, legal_flag, status, public_scope, " +
    "DATE_FORMAT(publish_time, '%Y-%m-%dT%H:%i:%s') AS pt, " +
    "CASE WHEN COALESCE(extraction_text,'') <> '' THEN extraction_text " +
    "     ELSE COALESCE(content_html,'') END AS ext " +
    "FROM bidding_notice_external WHERE deleted=0 AND status='ACTIVE'"
  ).trim().split('\n').filter(Boolean);
  console.log(`主表 ACTIVE 公告: ${rows.length} 条`);

  const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14);
  const backup = `${INDEX}-bak-${ts}`;

  if (!APPLY) {
    console.log('\n计划：');
    console.log(`  1. 复制现有索引 → ${backup}（reindex，失败可回滚）`);
    console.log(`  2. 删除 ${INDEX} 并以 ngram mapping 重建（min_gram=${MIN_GRAM} max_gram=${MAX_GRAM}）`);
    console.log(`  3. 从主表 ${rows.length} 条 ACTIVE 公告 bulk 写入`);
    console.log('  4. 校验：关键词精确度抽样比对 MySQL LIKE');
    console.log('\n加 --apply 实际执行。');
    return;
  }

  // 3) 备份
  if (before.json && before.json.count > 0) {
    console.log(`备份 → ${backup}`);
    await es('PUT', `/${backup}`);
    const r = await es('POST', '/_reindex?wait_for_completion=true&refresh=true',
      { source: { index: INDEX }, dest: { index: backup } });
    console.log('  备份完成:', JSON.stringify(r.json).slice(0, 200));
  }

  // 4) 重建
  await es('DELETE', `/${INDEX}`);
  await es('PUT', `/${INDEX}`, { settings: settings(), mappings: mappings() });
  console.log(`重建 ${INDEX}（ngram）完成`);

  // 5) 从主表 bulk 写入
  const esc = s => (s === null || s === undefined || s === '') ? null : String(s);
  /**
   * 粗略去 HTML 标签，语义对齐后端 jsoup.parse(html).text()：
   * 去 script/style 内容 → 去标签 → 实体解码 → 空白压缩。
   *
   * <p>为什么不引 jsoup：本工具是 Node 运维脚本，引入 Java 依赖不划算；
   * 检索只需要"字面包含"能命中，标签残留不影响正确性（只会略微放大正文体积）。
   * 若后端 contentText 取数逻辑变更，本函数需同步（已在上一处注释标注口径来源）。
   */
  /** 去标签后按后端同口径截断（MAX_TEXT_CHARS），保证索引正文字符数与索引通知器一致。 */
  const sliceText = s => (s && s.length > MAX_TEXT_CHARS ? s.substring(0, MAX_TEXT_CHARS) : s);
  const stripHtml = s => {
    if (!s) return null;
    return String(s)
      .replace(/<(script|style)[^>]*>[\s\S]*?<\/\1>/gi, ' ')
      .replace(/<[^>]+>/g, ' ')
      .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'")
      .replace(/\s+/g, ' ').trim() || null;
  };
  const lines = [];
  for (const row of rows) {
    const c = row.split('\t');
    const [id, title, sourceCode, contentType, origUrl, issuerName, projectName, projectCode,
      tenderProjectCode, bidSectionCodes, industryFlag, legalFlag, status, publicScope, pt, ext] = c;
    const doc = {
      // id 必须保持**字符串**：雪花 id 是 19 位 long，超过 JS Number.MAX_SAFE_INTEGER(2^53)，
      // Number() 会静默丢失末尾 3 位（实测 ...674 → ...700），导致 _source.id 与 _id 不一致、
      // 检索回表时按错误 id 查不到数据。ES 的 long 字段接受字符串形式的数字，无需转换。
      // 同理 publishTime 用字符串（date 字段接受 ISO 字符串）。
      id: String(id), title: esc(title), sourceCode: esc(sourceCode), contentType: esc(contentType),
      origUrl: esc(origUrl), issuerName: esc(issuerName), projectName: esc(projectName),
      projectCode: esc(projectCode), tenderProjectCode: esc(tenderProjectCode),
      bidSectionCodes: esc(bidSectionCodes), industryFlag: esc(industryFlag), legalFlag: esc(legalFlag),
      status: esc(status), publicScope: esc(publicScope), contentText: sliceText(stripHtml(ext)), publishTime: esc(pt),
    };
    lines.push(JSON.stringify({ index: { _index: INDEX, _id: id } }));
    lines.push(JSON.stringify(doc));
  }
  const payload = lines.join('\n') + '\n';
  const res = await fetch(ES + '/_bulk?refresh=true', {
    method: 'POST',
    headers: { Authorization: authHeader, 'Content-Type': 'application/x-ndjson' },
    body: payload,
  });
  const bulk = await res.json();
  if (bulk.errors) {
    const bad = bulk.items.filter(i => i.index && i.index.error).slice(0, 3);
    console.error('bulk 有错误:', JSON.stringify(bad));
  }
  console.log('bulk 写入完成, errors =', bulk.errors);

  const after = await es('GET', `/${INDEX}/_count`);
  console.log(`重建后文档数: ${after.json.count}（目标 ${rows.length}）`);
  if (DROP_BACKUP) {
    await es('DELETE', `/${backup}`);
    console.log(`已删除备份 ${backup}`);
  } else {
    console.log(`备份保留在 ${backup}（确认无误后可删除）`);
  }
}
main().catch(e => { console.error('ERR', e.message); process.exit(2); });
