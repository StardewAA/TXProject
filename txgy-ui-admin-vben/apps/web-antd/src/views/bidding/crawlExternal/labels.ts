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
 *   前端请求后端澄清/暴露生效值：见 docs/前端规范-全域接口说明/15-采集域参数语义与配置归属-2026-09-25.md。
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

/** 获取方式：统一"获取方式"这一种叫法与两种取值文案 */
export const FETCH_TYPE_LABEL = '获取方式';
export function fetchTypeText(t?: null | string): string {
  if (t === 'API') return 'API · 接口直取（JSON）';
  if (t === 'CRAWLER') return 'CRAWLER · 爬虫（列表 HTML）';
  return '—';
}

/** 获取方式：表单下拉用的**详细**文案（说明取值差异）；标签展示用上面的 fetchTypeText（短文案） */
export const FETCH_TYPE_OPTIONS = [
  { label: 'CRAWLER · 爬虫（列表 HTML / 逐条下载）', value: 'CRAWLER' },
  { label: 'API · 接口直取（JSON 列表 + 字段映射）', value: 'API' },
];

/** 下载通道：表单下拉选项（当前仅 GENERIC） */
export const CHANNEL_OPTIONS = [{ label: CHANNEL_GENERIC_TEXT, value: 'GENERIC' }];
