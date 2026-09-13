/* 采集域展示文案与口径 —— **单一真源**（同时也回答了"这个间隔到底管什么"）。
 *
 * 时间间隔的真源在后端（CrawlSourceDO.fetchIntervalSeconds 注释，2026-09-13 定稿）：
 *   **一个源一把尺子** —— 同一个 fetch_interval_seconds 同时约束两件事：
 *     ① 调度器多久触发一轮「列表发现」轮询（CrawlRunServiceImpl，30s tick 按源节流）；
 *     ② 该源的「下载器」每 interval 最多下载一次公告原文（CrawlDownloadGate 闸位 scope_key=SRC:<sourceCode>）。
 *   硬下限 120 秒（后端 MIN_INTERVAL_SECONDS，低于 120 不生效）。
 *   旧口径"源表管列表 + 全局 consumer-delay-ms 管下载"已废止；但 yml 的 consumer-delay-ms 仍被
 *   CrawlDownloadGate 当作"源未配置时的全局兜底"读取 ⇒ 界面上必须写清归属，否则运维看到 yml 里的
 *   120000 会以为那才是下载间隔（这就是"下载器间隔到底是列表还是详情页"这个疑问的来源）。
 *   另有两个界面看不见的节拍，与源级间隔**不是**同一件事：page-delay-ms=列表翻页间隔、entry-delay-ms=页内条目处理节拍。
 *   前端请求后端澄清/暴露生效值：见 docs/前端规范-全域接口说明/15-采集域参数语义与配置归属-2026-09-14.md。
 *
 * 用法：标签/文案一律从这里取，勿在页面里各写各的（此前同一字段有 5 种间隔写法、4 种通道叫法）。
 */

/** 抓取间隔硬下限（与后端 MIN_INTERVAL_SECONDS / 错误码 106_046 同口径） */
export const MIN_INTERVAL_SECONDS = 120;

/** 抓取间隔标签：表单 / 表格 / 详情统一用这个 */
export const INTERVAL_LABEL = '抓取间隔（秒）';

/** 抓取间隔含义（tooltip / 表单提示 / 详情副文案） */
export const INTERVAL_HINT =
  '同一值同时约束两件事：① 该源「列表发现」的轮询间隔；② 该源「公告原文下载」的最小间隔（下载闸按源计数）。硬下限 120 秒，低于 120 不生效。';

/** 抓取间隔展示：表格 / 详情 / 源卡统一 */
export function intervalText(v?: null | number): string {
  return v === null || v === undefined ? '—' : `${v} 秒`;
}

/** 下载通道：同一字段勿再有"下载通道适配器 / 下载通道 / 通道 / 通用下载"四种叫法 */
export const CHANNEL_LABEL = '下载通道';
export const CHANNEL_GENERIC_TEXT = 'GENERIC · 通用 HTTP 下载器';
export function channelText(code?: null | string): string {
  if (!code) return '—';
  return code === 'GENERIC' ? CHANNEL_GENERIC_TEXT : code;
}

/** 列表驱动方式：**只描述「列表怎么拿」，不描述正文形态**（依据后端 CrawlSourceRespVO.fetchType 与
 *  sql/2026-09-13-fetch-type-semantics.sql 的列注释："API=列表走 JSON 接口 / CRAWLER=列表走 HTML 页面"）。
 *  旧叫法"获取方式"会让人以为它描述**整个源**的类型 —— 秦源是 API(列表) 但正文仍逐条下载 HTML，
 *  于是界面把两个维度混在一处，读的人自然得出"这源到底是 API 还是爬虫"的错误问题。 */
export const FETCH_TYPE_LABEL = '列表驱动方式';
export const FETCH_TYPE_HINT =
  '只描述「列表怎么拿」；正文怎么取看「正文形态」与「正文解析器」。两者不可混用——PDF 源配 HTML_AI 会静默退化为本地 PDFBox 抽取。';
export function fetchTypeText(t?: null | string): string {
  if (t === 'API') return 'API · 列表走 JSON 接口';
  if (t === 'CRAWLER') return 'CRAWLER · 列表走 HTML 页面';
  return '—';
}

/** 列表驱动方式：表单下拉用的**详细**文案；标签展示用上面的 fetchTypeText（短文案） */
export const FETCH_TYPE_OPTIONS = [
  { label: 'CRAWLER · 列表走 HTML 页面（翻页）', value: 'CRAWLER' },
  { label: 'API · 列表走 JSON 接口（分页参数 + 字段映射）', value: 'API' },
];

/** 正文形态（与「列表驱动方式」是**两个维度**）：HTML=逐条下载详情页 / PDF=取直链下载原文 */
export const CONTENT_FORM_LABEL = '正文形态';
export function contentFormText(v?: null | string): string {
  if (v === 'HTML') return 'HTML · 逐条下载详情页';
  if (v === 'PDF') return 'PDF · 取直链下载原文';
  return '—';
}

/** 解析器分两侧：列表侧 parserCodeDefault（解析列表页/接口响应）/ 正文侧 detailParserCode（HTML_AI、PDF_AI） */
export const LIST_PARSER_LABEL = '列表解析器';
export const DETAIL_PARSER_LABEL = '正文解析器';
export function parserText(code?: null | string): string {
  return code || '—';
}

/** 下载通道：表单下拉选项（当前仅 GENERIC） */
export const CHANNEL_OPTIONS = [{ label: CHANNEL_GENERIC_TEXT, value: 'GENERIC' }];
