import type {
  FollowEvent,
  FollowItem,
  FollowItemPageParams,
  FollowReq,
  FollowStatus,
  InternalNoticeDetail,
  InternalNoticeItem,
  InternalNoticeParams,
  NoticeBodyBlock,
  NoticeCodeDim,
  NoticeExternalDetail,
  NoticeExternalItem,
  NoticePageParams,
  PageResult,
} from './types';

/**
 * notice-external 服务层：真实接口 ↔ 内置 Mock 切换。
 *
 * 真实接口（对齐 docs/外部公告-前端对接/01-接口契约.md + 后端 VO 源码，48110 实测）：
 *   GET {prefix}/notice-external/page?keyword=&noticeTypeRaw=&sourceCode=&contentType=&industryFlag=&beginTime=&endTime=&pageNo=&pageSize=
 *   GET {prefix}/notice-external/detail?id=xx
 *   GET {prefix}/notice-external/go?url=…    外链中转（白名单 host 校验，前端不直开外站）
 *   GET {prefix}/notice-follow/status?targetType=&targetKey=   关注状态单点查询（BR-2/N2②）
 *
 * 联调口径（后端 R1~R12 已实现，前端按此对接）：
 *   - 关键词全文检索 = keyword（ES 索引 bidding-notice-external，命中标题/项目/编号/正文，上限 200；
 *     ES 未启用时返回 1-060-106-015 → 页面提示“检索暂不可用”，R5/红线 4.6）；
 *   - title 为标题“精确匹配”筛选（大厅未用，保留契约字段）；
 *   - noticeTypeRaw 精确筛选已生效（eq，R1）：直接透传外站原文值；大厅“中标公示”
 *     快捷口径 = 候选类 + 结果类合并视图，由本层按原始值分组抓取合并（R1：后端不发明文聚合）；
 *   - 来源站 = 数据驱动（bidding_crawl_source：sourceCode+sourceName），列表行已带 sourceCode（R2/R3），
 *     筛选用 sourceCode，前端不再硬编码“本站/示例平台”映射；
 *   - 时间出参为 epoch 毫秒；入参 beginTime/endTime 归一为 'yyyy-MM-dd HH:mm:ss'（后端 @DateTimeFormat 口径）；
 *   - 正文渲染优先级：bodyBlocksStatus=SUCCESS 且 bodyBlocksJson 非空 → 按结构化块渲染；
 *     否则回退 contentHtmlSafe（jsoup 白名单净化版，A 级安全：contentHtml 恒 null 不下发）。
 *   - 安全：文本一律先转义，HTML 走 v-dompurify-html 二次净化（纵深防御）。
 */
import { biddingConfig } from './config';
import { cleanT, toCleanList } from './format';
import {
  mockDetail,
  mockFollowed,
  mockFollowStatus,
  mockInternalDetail,
  mockInternalPage,
  mockMyFollow,
  mockPage,
  mockReadItem,
  mockTimeline,
  mockToggleFollow,
} from './mock';

const API = `${biddingConfig.baseUrl}${biddingConfig.apiPrefix}/notice-external`;

/** 大厅“中标公示”快捷口径标记（候选类 + 结果类合并视图，见 RESULT_RAW_KW） */
export const RESULT_TYPE_MERGE = '__result_merge__';

/**
 * 外部公告域错误码段 1_060_106_xxx（enums/ErrorCodeConstants.java 已登记）；
 * 前端只映射“展示语义”需要的少数码，其余原样展示 msg。
 */
const CODE_NOT_EXISTS = 1060106001; // 外部公告不存在（R8）
const CODE_TAKEN_DOWN = 1060106002; // 外部公告已下线或不可展示（R8）
const CODE_SEARCH_UNAVAILABLE = 1060106015; // keyword 检索未启用（bidding.es.enabled=false，02 §6）

/** 带错误码的接口异常（便于调用层按码给友好提示） */
class ApiError extends Error {
  code?: number;

  constructor(msg: string, code?: number) {
    super(msg);
    this.name = 'ApiError';
    this.code = code;
  }
}

function unwrap<T>(body: { code?: number; data?: T; msg?: string }): T {
  if (body?.code === undefined) return body as T; // 兼容直接返回对象
  if (body.code !== 0) {
    throw new ApiError(body.msg || `公告接口错误：code=${body.code}`, body.code);
  }
  return body.data as T;
}

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!res.ok) {
    throw new Error(`公告接口请求失败：HTTP ${res.status}`);
  }
  // 本框架失败响应是 HTTP 200 + {code,msg}（由下面的 unwrap 按 code 判定），
  // 非 JSON（网关 HTML 错误页等）先给出可读原因，避免 res.json() 抛 "Unexpected token <"
  const contentType = (res.headers.get('content-type') || '').toLowerCase();
  if (!contentType.includes('json')) {
    throw new Error(
      `公告接口响应不是 JSON（HTTP ${res.status}，content-type=${contentType || '未知'}）`,
    );
  }
  const body = await res.json();
  return unwrap<T>(body);
}

/* ---------- 时间归一（契约 02 §5：出参 epoch 毫秒；入参 beginTime/endTime 传 'yyyy-MM-dd HH:mm:ss'） ---------- */

function quickToBegin(quick?: string): string | undefined {
  if (!quick || quick === '全部时间') return undefined;
  if (quick === '今年以来') {
    // 自然年口径：当年 1 月 1 日 00:00:00（原 365 天回溯=去年今天起，语义错误）
    const y = new Date().getFullYear();
    return `${y}-01-01 00:00:00`;
  }
  // 键必须与 QUICK_RANGES 的标签**逐字一致**（标签即本函数的入参；改标签须同步改这里）
  const map: Record<string, number> = { '最近 7 天': 7, '最近 30 天': 30, '最近 90 天': 90 };
  const days = map[quick];
  if (!days) return undefined;
  const t = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} 00:00:00`;
}

const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/;
/** 日期/时间串 → 接口可解析的 'yyyy-MM-dd HH:mm:ss'（仅日期自动补起止时刻） */
function normTime(v: number | string | undefined, isEnd: boolean): string | undefined {
  if (v === undefined || v === null || v === '') return undefined;
  const s = String(v);
  if (DATE_ONLY_RE.test(s)) return isEnd ? `${s} 23:59:59` : `${s} 00:00:00`;
  return s;
}

/* ---------- 入参映射（界面文案 → 接口值域；空值不下发） ---------- */

/** 内容形态：界面标签 ↔ 接口 code（真值域 HTML/PDF/ATTACHMENT/IMAGE/OTHER） */
const CONTENT_TYPE_CODE: Record<string, string> = {
  'HTML 网页': 'HTML',
  'PDF 文件': 'PDF',
  附件下载: 'ATTACHMENT',
  图片: 'IMAGE',
  其他: 'OTHER',
  HTML: 'HTML',
  PDF: 'PDF',
  ATTACHMENT: 'ATTACHMENT',
  IMAGE: 'IMAGE',
  OTHER: 'OTHER',
};

/** 大厅“中标公示”快捷口径分组（外站原文值域无权威码，由前端按字面组合，R1）：
 * 候选类：含“候选”；结果类：不含“候选”但含 中标/结果/成交/定标/中标人 等结果语义。
 * 只作用于列表已发现的 noticeTypeRaw 值，未知原文一律按精确值过滤。 */
const CANDIDATE_KW = /候选/;
const RESULT_RAW_KW = /中标|结果|成交|定标/;

/** 判断某外站类型原文属于“候选”还是“结果”语义组（用于合并视图） */
export function classifyResultRaw(raw: string): 'candidate' | 'result' | null {
  if (!raw) return null;
  if (CANDIDATE_KW.test(raw)) return 'candidate';
  return RESULT_RAW_KW.test(raw) ? 'result' : null;
}

function toQueryPlain(params: NoticePageParams & { quick?: string }): Record<string, string> {
  const out: Record<string, string> = {};
  const kw = cleanT(params.keyword);
  if (kw) out.keyword = kw;
  const title = cleanT(params.title);
  if (title) out.title = title; // 标题精确匹配（契约字段；大厅未用）
  const src = cleanT(params.sourceCode);
  if (src) out.sourceCode = src;
  const ct = CONTENT_TYPE_CODE[String(params.contentType ?? '')];
  if (ct) out.contentType = ct;
  const ind = cleanT(params.industryFlag);
  if (ind) out.industryFlag = ind;
  // noticeTypeRaw 精确筛选已实现（eq）；合并视图标记由 fetchMerged 处理，不下发
  if (params.noticeTypeRaw && params.noticeTypeRaw !== RESULT_TYPE_MERGE) {
    out.noticeTypeRaw = params.noticeTypeRaw;
  }
  const pc = cleanT(params.projectCode);
  if (pc) out.projectCode = pc;
  // 编号精确搜索的单维度口径（三参同传是 AND 关系，见 types.ts NoticePageParams.codeSearch）：
  // 这里只做「调用方显式指定了某一维度」时的透传；大厅「全部维度」由 fetchCodeMerged 拆请求合并
  const tpc = cleanT(params.tenderProjectCode);
  if (tpc) out.tenderProjectCode = tpc;
  const bsc = cleanT(params.bidSectionCode);
  if (bsc) out.bidSectionCode = bsc;
  const begin = normTime((params.beginTime as string | undefined) ?? quickToBegin(params.quick), false);
  const end = normTime(params.endTime as string | undefined, true);
  if (begin) out.beginTime = begin;
  if (end) out.endTime = end;
  out.pageNo = String(params.pageNo ?? 1);
  out.pageSize = String(Math.min(params.pageSize ?? 10, 100)); // R9：pageSize ≤ 100
  return out;
}

/** 列表行归一化：空值字串 "NULL" → 空（R11 根治后仍保留兜底） */
function normalizeRow(r: NoticeExternalItem): NoticeExternalItem {
  return {
    ...r,
    title: cleanT(r.title) || r.title,
    issuerName: r.issuerName ? cleanT(r.issuerName) : undefined,
    sourceName: r.sourceName ? cleanT(r.sourceName) : undefined,
    noticeTypeRaw: r.noticeTypeRaw ? cleanT(r.noticeTypeRaw) : null,
    legalFlag: r.legalFlag ? cleanT(r.legalFlag) : null,
    industryFlag: r.industryFlag ? cleanT(r.industryFlag) : null,
    status: r.status ? cleanT(r.status) : null,
  };
}

/** 单页抓取（真实接口 / Mock 共用出入口） */
async function pageOnce(plain: Record<string, string>): Promise<PageResult<NoticeExternalItem>> {
  const data = biddingConfig.useMock
    ? await mockPage({ ...plain })
    : await getJson<PageResult<NoticeExternalItem>>(
        `${API}/page?${new URLSearchParams(plain).toString()}`,
      );
  return { total: data.total, list: (data.list || []).map(normalizeRow) };
}

/** 合并视图行数上限（对齐 keyword 命中集上限 01 §1.1）：命中该上限时页脚提示「仅展示前 200 条」 */
const MERGE_MAX_ROWS = 200;
/** 合并视图单页抓取条数（接口上限 100） */
const MERGE_PAGE_SIZE = 100;

/** 合并集排序：发布时间倒序（后端已按 publishTime 倒序返回，此处统一多来源合并后的次序；epoch 毫秒与字符串都兼容） */
function byPublishTimeDesc(a: NoticeExternalItem, b: NoticeExternalItem): number {
  const pa = a.publishTime ?? 0;
  const pb = b.publishTime ?? 0;
  if (typeof pa === 'number' && typeof pb === 'number') return pb - pa;
  const sa = String(pa);
  const sb = String(pb);
  if (sa === sb) return 0;
  return sa < sb ? 1 : -1; // 字符串时间格式可字典序倒排
}

/** 合并视图抓取（“中标公示”= 候选类 + 结果类；后端 eq 单值，前端分组抓取合并） */
async function fetchMerged(
  params: NoticePageParams & { quick?: string },
): Promise<PageResult<NoticeExternalItem>> {
  const pageNo = params.pageNo ?? 1;
  const pageSize = Math.min(params.pageSize ?? 10, 100);
  const raws: string[] = (Array.isArray(params.resultRaws) ? params.resultRaws : []).filter(Boolean);
  if (!raws.length) return { list: [], total: 0 };

  const base = toQueryPlain({ ...params, noticeTypeRaw: undefined });
  // 合并视图按组逐原始值抓全（分页上限 100/页；行数上限见 MERGE_MAX_ROWS）
  const acc = new Map<string, NoticeExternalItem>();
  for (const raw of raws) {
    for (let p = 1; ; p += 1) {
      const plain: Record<string, string> = {
        ...base,
        noticeTypeRaw: raw,
        pageNo: String(p),
        pageSize: String(MERGE_PAGE_SIZE),
      };
      const res = await pageOnce(plain);
      const rows = res.list || [];
      for (const r of rows) {
        if (!acc.has(String(r.id))) acc.set(String(r.id), r);
      }
      if (acc.size >= MERGE_MAX_ROWS) break;
      if (!res.total || rows.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= res.total) break;
    }
    // 到达行数上限即停止处理后续原始值——原 `break outer` 的语义，改用"内层 break + 外层判断"表达，
    // 因为仓库 lint 规则 no-restricted-syntax 禁止 LabeledStatement（该错误此前一直存在）。
    if (acc.size >= MERGE_MAX_ROWS) break;
  }
  const all = [...acc.values()].sort(byPublishTimeDesc);
  const start = (pageNo - 1) * pageSize;
  // truncated：合并视图命中 MAX_ROWS 上限（页脚据此提示"仅展示前 200 条"，避免 total 失真无感知）
  return {
    total: all.length,
    list: all.slice(start, start + pageSize),
    truncated: all.length >= MERGE_MAX_ROWS,
  };
}

/* ================= 编号精确搜索（三维度分别请求后合并，2026-10-01 新增） =================
 * 后端 page 的三个编号过滤参数 projectCode / tenderProjectCode / bidSectionCode 在**同一次请求里是
 * AND 关系**：实测（网关 48080，全量库 total=1637）同一个输入值同传三参 → total=0；而单传三个参数
 * 分别命中 1 / 1 / 183 条。所以大厅的「全部维度」绝不能把一个输入值同时塞进多个参数，改用与
 * 「中标公示」合并视图（fetchMerged）完全相同的口径：按维度各发一次请求（**并发**）→ 按公告 id 去重
 * → publishTime 倒序 → 在合并集上分页，total 取合并后条数。
 * 覆盖率（该编号字段有值的公告占比，**ACTIVE 口径**实测基线，常量见下方 CODE_DIMS）：
 * 项目编号 88.7% / 招标项目编号 1.0% / 标段编号 15.0%
 * —— 后两者「搜不到」属正常，界面据此给覆盖率提示（见 index.vue 的编号覆盖率提示条）。
 */

/** 编号维度元数据（界面维度名 + 后端参数名 + 字段覆盖率） */
export interface CodeDimMeta {
  /** 该编号字段的公告有值覆盖率（0~1，实测基线；界面提示口径） */
  coverage: number;
  dim: NoticeCodeDim;
  /** 界面维度名 */
  label: string;
  /** 后端 page 参数名（当前与 dim 同名，显式列出便于阅读） */
  param: string;
}

/** 维度元数据：顺序即界面「全部维度」的展开顺序（也是并发请求的发起顺序）。
 *
 *  coverage = 该编号字段的**有值覆盖率**，口径与取数（2026-09-23 实测，本机库、与大厅同口径）：
 *    分母 = `bidding_notice_external` 中 `status='ACTIVE'` 的公告（大厅只展示已生效公告）1637 条；
 *    分子 = 该编号列非空的条数：project_code 1452 / tender_project_code 16 / bid_section_codes 246。
 *    → 88.7% / 1.0% / 15.0%。
 *  比对：全表口径（含未生效 NEW，2199 条）为 86.9% / 1.5% / 11.2% —— 与大厅展示口径不符，
 *  故界面提示一律用 ACTIVE 口径；改数据源口径时**必须同步重算**（否则提示会失真成"搜不到是正常的"）。
 *  单一口径来源：门户各处（大厅「编号精确」提示、投标流程管理的「关联公告」弹窗）都取本常量，不再各写一份。 */
export const CODE_DIMS: CodeDimMeta[] = [
  { dim: 'projectCode', label: '项目编号', param: 'projectCode', coverage: 0.887 },
  { dim: 'tenderProjectCode', label: '招标项目编号', param: 'tenderProjectCode', coverage: 0.01 },
  { dim: 'bidSectionCode', label: '标段编号', param: 'bidSectionCode', coverage: 0.15 },
];

/** 「全部维度」= 三个维度并发（大厅默认口径） */
export const CODE_ALL_DIMS: NoticeCodeDim[] = CODE_DIMS.map((d) => d.dim);

/** 覆盖率阈值：低于它的维度在界面上提示「该编号字段仅约 N% 的公告有值，搜不到属正常」 */
export const CODE_LOW_COVERAGE = 0.3;

/** 归一维度选择：丢弃未知值、去重；空选择按「全部维度」处理（默认口径，绝不静默退化成「不过滤」） */
function normalizeCodeDims(dims?: NoticeCodeDim[]): CodeDimMeta[] {
  const metas: CodeDimMeta[] = [];
  for (const dim of dims ?? []) {
    const meta = CODE_DIMS.find((d) => d.dim === dim);
    if (meta && !metas.includes(meta)) metas.push(meta);
  }
  return metas.length ? metas : [...CODE_DIMS];
}

/** Mock 态的行级维度匹配：mock.ts 的 mockPage 未实现这三个编号参数，
 *  不补这层，演示态输入编号会「什么都没过滤掉」，把功能缺失演示成正常。真实态不做行级过滤（后端负责）。 */
function mockRowHitsDim(row: NoticeExternalItem, dim: NoticeCodeDim, value: string): boolean {
  if (dim === 'projectCode') return cleanT(row.projectKey) === value;
  if (dim === 'tenderProjectCode') return cleanT(row.tenderProjectKey) === value;
  return toCleanList(row.bidSectionKeys).some((k) => cleanT(k) === value);
}

/** 抓取一个「筛选片段」的全部分页（每页 100，至多 MERGE_MAX_ROWS 行）。
 *  后端已按 publishTime 倒序返回（NoticeExternalServiceImpl#getPage：orderByDesc(publishTime)），
 *  故每个片段取到的前 N 行即该片段最新的 N 行 —— 多片段合并后整体再取前 N 行，
 *  结果就是「全量并集里最新的 N 行」，不会因为先抓哪个维度而漏掉更新的公告。 */
async function fetchFragmentRows(
  base: Record<string, string>,
  fragment: Record<string, string>,
  accept?: (row: NoticeExternalItem) => boolean,
): Promise<NoticeExternalItem[]> {
  const out: NoticeExternalItem[] = [];
  for (let p = 1; out.length < MERGE_MAX_ROWS; p += 1) {
    const res = await pageOnce({
      ...base,
      ...fragment,
      pageNo: String(p),
      pageSize: String(MERGE_PAGE_SIZE),
    });
    const raw = res.list || [];
    out.push(...(accept ? raw.filter(accept) : raw));
    if (!res.total || raw.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= res.total) break;
  }
  return out.slice(0, MERGE_MAX_ROWS);
}

/** 合并集分页：多片段结果按公告 id 去重 → publishTime 倒序 → 取前 MERGE_MAX_ROWS 行 → 在其中切当前页 */
function mergeAndPaginate(
  groups: NoticeExternalItem[][],
  pageNo: number,
  pageSize: number,
): PageResult<NoticeExternalItem> {
  const acc = new Map<string, NoticeExternalItem>();
  for (const rows of groups) {
    for (const r of rows) {
      if (!acc.has(String(r.id))) acc.set(String(r.id), r);
    }
  }
  const all = [...acc.values()].sort(byPublishTimeDesc);
  const capped = all.slice(0, MERGE_MAX_ROWS);
  const start = (pageNo - 1) * pageSize;
  // truncated：合并集命中行数上限（页脚据此提示「仅展示前 200 条」，避免把合并集 total 当全量）
  return {
    list: capped.slice(start, start + pageSize),
    total: capped.length,
    truncated: all.length >= MERGE_MAX_ROWS,
  };
}

/** 编号精确搜索抓取：选中维度（×「中标公示」分组）各发一次请求（并发）→ 合并去重分页 */
async function fetchCodeMerged(
  params: NoticePageParams & { quick?: string },
  value: string,
): Promise<PageResult<NoticeExternalItem>> {
  const mergeMode = params.noticeTypeRaw === RESULT_TYPE_MERGE;
  const mergeRaws: string[] = (Array.isArray(params.resultRaws) ? params.resultRaws : []).filter(Boolean);
  // 与合并视图同口径：勾了「中标公示」却没有已发现的原站类型时无结果（而不是退化成不过滤）
  if (mergeMode && !mergeRaws.length) return { list: [], total: 0 };

  // 基础条件照常叠加（关键词/来源站/内容形态/行业/时间都在 base 里），编号维度只是额外一层过滤
  const base = toQueryPlain({ ...params, noticeTypeRaw: undefined });
  // 维度之间、合并视图分组之间都是并集关系：交叉后各自抓取，再统一合并去重
  const fragments: Array<{ dim: NoticeCodeDim; fragment: Record<string, string>; }> = [];
  for (const meta of normalizeCodeDims(params.codeSearch?.dims)) {
    for (const raw of mergeMode ? mergeRaws : [undefined]) {
      fragments.push({
        dim: meta.dim,
        fragment: raw ? { [meta.param]: value, noticeTypeRaw: raw } : { [meta.param]: value },
      });
    }
  }
  // 并发发起（维度/分组之间互不依赖；单片段内部仍按页串行）
  const groups = await Promise.all(
    fragments.map(({ dim, fragment }) => {
      const accept = biddingConfig.useMock
        ? (row: NoticeExternalItem) => mockRowHitsDim(row, dim, value)
        : undefined;
      return fetchFragmentRows(base, fragment, accept);
    }),
  );
  return mergeAndPaginate(groups, params.pageNo ?? 1, Math.min(params.pageSize ?? 10, 100));
}

/** 列表（大厅公告目录） */
export async function fetchNoticePage(
  params: NoticePageParams & { quick?: string },
): Promise<PageResult<NoticeExternalItem>> {
  try {
    // 编号精确搜索：值非空才启用（空值不下发，与关键词口径一致）；三个维度拆成并发请求后合并
    const code = cleanT(params.codeSearch?.value);
    if (code) {
      return await fetchCodeMerged(params, code);
    }
    if (params.noticeTypeRaw === RESULT_TYPE_MERGE) {
      return await fetchMerged(params);
    }
    return await pageOnce(toQueryPlain(params));
  } catch (e) {
    if (e instanceof ApiError && e.code === CODE_SEARCH_UNAVAILABLE) {
      // 02 §6：后端未启用 ES 检索组件时传 keyword 报 1-060-106-015，前端提示“检索暂不可用”
      throw new Error('关键词全文检索暂不可用（服务端未启用检索组件）：请清空搜索词后重试');
    }
    throw e;
  }
}

/* ---------- 详情 ---------- */

function parseBodyBlocks(
  rawJson: null | string | undefined,
  preset?: NoticeBodyBlock[],
): NoticeBodyBlock[] {
  if (preset) return preset;
  if (!rawJson) return [];
  try {
    const arr = JSON.parse(rawJson) as unknown;
    if (!Array.isArray(arr)) return [];
    return arr.filter(
      (b): b is NoticeBodyBlock =>
        !!b &&
        typeof b.type === 'string' &&
        ['heading', 'list', 'paragraph', 'table'].includes(b.type),
    );
  } catch {
    return [];
  }
}

function normalizeDetail(d: NoticeExternalDetail): NoticeExternalDetail {
  const preset = Array.isArray(d.bodyBlocks) ? d.bodyBlocks : undefined;
  return {
    ...d,
    // attachments 可为 null（实测）：归一化为数组；INFECTED 附件后端已不下发链接（R6）
    attachments: d.attachments ?? [],
    title: cleanT(d.title) || d.title,
    issuerName: d.issuerName ? cleanT(d.issuerName) : undefined,
    sourceName: d.sourceName ? cleanT(d.sourceName) : undefined,
    sourceCode: d.sourceCode ? cleanT(d.sourceCode) : undefined,
    noticeTypeRaw: d.noticeTypeRaw ? cleanT(d.noticeTypeRaw) : null,
    legalFlag: d.legalFlag ? cleanT(d.legalFlag) : null,
    industryFlag: d.industryFlag ? cleanT(d.industryFlag) : null,
    projectName: d.projectName ? cleanT(d.projectName) : undefined,
    projectKey: d.projectKey ? cleanT(d.projectKey) : undefined,
    tenderProjectKey: d.tenderProjectKey ? cleanT(d.tenderProjectKey) : undefined,
    tenderProjectName: d.tenderProjectName ? cleanT(d.tenderProjectName) : undefined,
    bodyParserCode: d.bodyParserCode ? cleanT(d.bodyParserCode) : null,
    bodyBlocksStatus: d.bodyBlocksStatus ? cleanT(d.bodyBlocksStatus) : null,
    // 结构化块：SUCCESS 且 JSON 数组非空时前端按块渲染（01 §7.1 / 02 §4）；否则回退 contentHtmlSafe
    bodyBlocks: parseBodyBlocks(d.bodyBlocksJson, preset),
    bidSectionCodes: toCleanList(d.bidSectionCodes),
    bidSectionKeys: toCleanList(d.bidSectionKeys),
    bidSectionNames: toCleanList(d.bidSectionNames),
  };
}

/** 详情 */
export async function fetchNoticeDetail(id: number | string): Promise<NoticeExternalDetail> {
  if (biddingConfig.useMock) {
    const hit = mockDetail(String(id));
    if (!hit) throw new Error('公告不存在或已下架');
    return normalizeDetail(hit);
  }
  let data: NoticeExternalDetail;
  try {
    data = await getJson<NoticeExternalDetail>(
      `${API}/detail?id=${encodeURIComponent(String(id))}`,
    );
  } catch (e) {
    // R8：detail 不存在（106-001）/ 已下架或非公开（106-002）→ 统一提示“公告不存在或已下架”
    if (e instanceof ApiError && (e.code === CODE_NOT_EXISTS || e.code === CODE_TAKEN_DOWN)) {
      throw new Error('公告不存在或已下架');
    }
    throw e;
  }
  return normalizeDetail(data);
}

/** “前往原地址查看”：真实态一律走后端 /go 中转（白名单校验 + 防开放重定向，02 §3.3） */
export function openOrigUrl(origUrl?: null | string): void {
  if (!origUrl) return;
  if (biddingConfig.useMock) {
    // Mock 态无 go 服务，直接打开原地址便于演示
    window.open(origUrl, '_blank', 'noopener,noreferrer');
    return;
  }
  const go = `${API}/go?url=${encodeURIComponent(origUrl)}`;
  window.open(go, '_blank', 'noopener,noreferrer');
}

/* ================= 大厅「本站」来源（2026-09-23 新增） =================
 * 使用方口径：“大厅的来源站里少一个**本站**”。本平台自己发布的公告（`/notice/public/list`，公开口径
 * 只有 PUBLISHED + 非内部 + SOCIAL）与外部采集公告同处一个大厅：
 *   · 来源站选「本站」→ 只列本站公告；
 *   · **默认视图**（未加任何筛选）→ 外部 + 本站**合并**按发布时间倒序、在合并集上分页（口径与
 *     「中标公示」合并视图一致，行数上限同样 200 并在页脚提示截断）；
 *   · 一旦用了外部专属筛选（关键词/性质/形态/行业/时间/编号精确）→ 只列外部公告，并由界面明示
 *     “当前筛选只作用于外部公告” —— 既不把本站静默漏掉当全量，也不把不适用筛选的本站混进来。
 * 为什么关键词不合并：本站公告的关键词查询走 noticeSearchEngine（索引 bidding-notice-external），
 * 拿外部索引的命中去查本站表得不到正确结果 —— 宁可不合并，也不返回错结果。
 */
export const SELF_SOURCE = '__self__';

/** 大厅行：外部公告行 + 本站公告行（sourceKind 决定点击去向与「本站」标记） */
export interface HallRow extends NoticeExternalItem {
  /** 本站公告类型中文名（外站行没有该字段；界面按需展示） */
  noticeTypeName?: null | string;
  /** 本站公告编号（notice_code，如 B6100002026000005）；关键词/编号查询本地匹配用 */
  noticeCode?: null | string;
  /** EXTERNAL=外站采集（详情走 /portal/bidding/detail/:id）；SELF=本站（走 internal-detail/:id） */
  sourceKind: 'EXTERNAL' | 'SELF';
}

/** 本站公告行 → 大厅行（字段对位：publisherName→issuerName、projectCode→projectKey） */
function toHallSelfRow(r: InternalNoticeItem): HallRow {
  return {
    id: r.id,
    issuerName: r.publisherName ?? null,
    noticeCode: r.noticeCode ?? null,
    noticeTypeName: r.noticeTypeName ?? r.noticeType ?? null,
    projectKey: r.projectCode ?? null,
    projectName: r.projectName ?? null,
    publishTime: r.publishTime ?? null,
    sourceKind: 'SELF',
    sourceName: '本站',
    status: r.status ?? null,
    title: r.title,
  };
}

/**
 * 大厅筛选 → 本站公开分页入参（纯函数，单测覆盖）。
 *
 * 口径：**能翻译的照翻，翻译不了的一律透传让它"查不到"，绝不静默放宽**。
 *   · 命中：时间区间 → publishTimeStart/End；项目编号 → projectCode；关键词 → keyword；
 *     公告性质原文 → noticeType（外站原文值与本站类型码不是一个编码空间，透传的结果是 0 行 —— 这是
 *     想要的"失败收敛"：用户看到的是"该筛选下没有"，而不是"筛选被忽略后的全部"）；
 *   · 形态/行业没有对应字段 → 不透传，由界面提示条说明该筛选对本站不生效。
 * 「中标公示」合并标记不是单值，由 fetchHallPage 单独走两次类型请求后合并（见 SELF_RESULT_TYPES）。
 */
export function selfQueryOf(params: NoticePageParams): InternalNoticeParams {
  const out: InternalNoticeParams = {};
  const begin = cleanT(params.beginTime);
  const end = cleanT(params.endTime);
  const keyword = cleanT(params.keyword);
  const projectCode = cleanT(params.projectCode);
  const noticeType = cleanT(params.noticeTypeRaw);
  if (begin) out.publishTimeStart = begin;
  if (end) out.publishTimeEnd = end;
  if (keyword) out.keyword = keyword;
  if (projectCode) out.projectCode = projectCode;
  if (noticeType && noticeType !== RESULT_TYPE_MERGE) out.noticeType = noticeType;
  return out;
}

/** 「中标公示」在本站的对应类型（候选类 + 结果类；对外站是原始值分组，对本站是数据集类型码） */
const SELF_RESULT_TYPES = ['WIN_CANDIDATE_PUBLICITY', 'WIN_RESULT_PUBLICITY'];

/**
 * 判断"是否合并本站"所需的筛选字段（**只要筛选条件**，不含分页：界面上的提示条也要用它，
 * 那里手里只有筛选状态、没有分页参数）。
 */
export interface HallMergeProbe {
  beginTime?: number | string;
  bidSectionCode?: string;
  codeSearch?: { dims: NoticeCodeDim[]; value: string };
  contentType?: string;
  endTime?: number | string;
  industryFlag?: string;
  keyword?: string;
  noticeTypeRaw?: string;
  pageNo?: number;
  pageSize?: number;
  projectCode?: string;
  quick?: string;
  sourceCode?: string;
  tenderProjectCode?: string;
  title?: string;
}

/**
 * 是否在**默认视图**（可与本站合并）。
 * 只要带任一外部专属筛选就返回 false —— 那些条件对本站公告无对应字段，合并会给出自相矛盾的结果。
 *
 * 例外（2026-10-02）：**关键词/编号查询也合并本站**。原先关键词被当作"外部专属筛选"排除本站，
 * 用户在大厅按编号（如 B6100002026000005）或标题关键词查询时**永远查不到本平台发布的公告**；
 *   · 关键词：实测后端 /notice/public/list?keyword= 对本站公告返回空（检索索引只覆盖外部域），
 *     本站侧「拉取一页 + 本地按 编号/标题/项目名 匹配」（见 fetchSelfAllRows/selfKeywordHit）；
 *   · 编号：public/list 的 bidSectionCode/projectCode/tenderProjectCode 编号过滤是**结构化精确参数**，
 *     实测可命中本站公告（探针：bidSectionCode=I6106002026000001001001 → B6100002026000005），
 *     本站侧按选中维度拆请求真实检索（见 fetchSelfAllRows）。
 */
export function hallMergesSelf(params: HallMergeProbe): boolean {
  return (
    !cleanT(params.title) &&
    !cleanT(params.noticeTypeRaw) &&
    !cleanT(params.sourceCode) &&
    !cleanT(params.contentType) &&
    !cleanT(params.industryFlag) &&
    !cleanT(params.projectCode) &&
    !cleanT(params.tenderProjectCode) &&
    !cleanT(params.bidSectionCode) &&
    !cleanT(params.beginTime) &&
    !cleanT(params.endTime) &&
    (params.quick === undefined || params.quick === '')
  );
}

/** 本站公告关键词匹配（编号/标题/项目编号/项目名/发布人；大小写不敏感）——
 *  后端 /notice/public/list 的 keyword 走检索组件、不含本站公告（实测返回空），
 *  故关键词/编号查询在本站侧由前端本地匹配兜底。 */
export function selfKeywordHit(row: HallRow, keyword: string): boolean {
  const k = cleanT(keyword).toLowerCase();
  if (!k) return true;
  const fields = [row.noticeCode, row.title, row.projectKey, row.projectName, row.issuerName];
  return fields.some((v) => typeof v === 'string' && v.toLowerCase().includes(k));
}

/** 本站公告抓取（公开只读；单页上限 100，与外部公告合并口径一致） */
async function fetchSelfRows(
  params: NoticePageParams,
  pageNo: number,
  pageSize: number,
): Promise<PageResult<HallRow>> {
  const res = await fetchInternalNotices({ ...selfQueryOf(params), pageNo, pageSize });
  return { list: (res.list || []).map((r) => toHallSelfRow(r)), total: res.total ?? 0 };
}

/** 按类型抓全本站公告（「中标公示」合并口径用：本站侧是数据集类型码，外站侧是原始值分组） */
async function fetchSelfAllRowsByType(
  params: NoticePageParams,
  noticeType: string,
): Promise<{ rows: HallRow[]; total: number }> {
  const rows: HallRow[] = [];
  let total = 0;
  for (let p = 1; rows.length < MERGE_MAX_ROWS; p += 1) {
    const res = await fetchInternalNotices({
      ...selfQueryOf(params),
      noticeType,
      pageNo: p,
      pageSize: MERGE_PAGE_SIZE,
    });
    total = res.total ?? total;
    const list = (res.list || []).map((r) => toHallSelfRow(r));
    rows.push(...list);
    if (!list.length || list.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= total) break;
  }
  return { rows: rows.slice(0, MERGE_MAX_ROWS), total: Math.max(total, rows.length) };
}

/**
 * 本站合并集分页（纯函数，单测覆盖）：按公告 id 去重 → publishTime 倒序 → 取前 MERGE_MAX_ROWS → 切页。
 * total 用两类型接口的真实之和（不是合并集长度），truncated 如实反映"参与合并的行被上限截了"。
 */
export function mergeSelfRows(
  rows: HallRow[],
  total: number,
  pageNo: number,
  pageSize: number,
): PageResult<HallRow> & { truncated?: boolean } {
  const acc = new Map<string, HallRow>();
  for (const row of rows) {
    if (!acc.has(String(row.id))) acc.set(String(row.id), row);
  }
  const capped = [...acc.values()].sort(byPublishTimeDesc).slice(0, MERGE_MAX_ROWS);
  const start = (pageNo - 1) * pageSize;
  return {
    list: capped.slice(start, start + pageSize),
    total,
    truncated: total > capped.length,
  };
}

/** 本站公告全量抓取（默认视图合并用；行数至多 MERGE_MAX_ROWS，total 取接口真实值） */
async function fetchSelfAllRows(
  params: NoticePageParams,
): Promise<{ rows: HallRow[]; total: number; truncated?: boolean }> {
  // 编号精确搜索（2026-10-02）：本站侧按选中维度**真实检索** public/list —— 编号过滤是结构化
  // 精确参数（bidSectionCode/projectCode/tenderProjectCode），实测可命中本站公告
  // （探针：bidSectionCode=I6106002026000001001001 → B6100002026000005）。
  const code = cleanT(params.codeSearch?.value);
  if (code) {
    const dims = normalizeCodeDims(params.codeSearch?.dims);
    const seen = new Set<string>();
    let rows: HallRow[] = [];
    let total = 0;
    for (const meta of dims) {
      for (let p = 1; rows.length < MERGE_MAX_ROWS; p += 1) {
        const res = await fetchInternalNotices({
          pageNo: p,
          pageSize: MERGE_PAGE_SIZE,
          [meta.param]: code,
        } as InternalNoticeParams);
        const list = (res.list || []).map(toHallSelfRow);
        rows.push(...list);
        total = Math.max(total, res.total ?? 0);
        if (!list.length || list.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= (res.total ?? 0)) break;
      }
    }
    // 维度之间是并集关系：同一公告可能被多个维度命中，去重后参与合并
    rows = rows.filter((row) => (seen.has(String(row.id)) ? false : (seen.add(String(row.id)), true)));
    return {
      rows: rows.slice(0, MERGE_MAX_ROWS),
      total: Math.max(total, rows.length),
      truncated: total > rows.length,
    };
  }
  // 关键词/编号查询：后端检索不含本站公告（实测 keyword 返回空），本站侧改为
  // 「不带 keyword 拉取（上限 MERGE_MAX_ROWS）→ 本地按 编号/标题/项目 匹配」。
  const kw = cleanT(params.keyword);
  if (kw) {
    const all: HallRow[] = [];
    let rawTotal = 0;
    for (let p = 1; all.length < MERGE_MAX_ROWS; p += 1) {
      const res = await fetchSelfRows({ ...params, keyword: undefined }, p, MERGE_PAGE_SIZE);
      rawTotal = res.total ?? rawTotal;
      const list = res.list || [];
      all.push(...list);
      if (!list.length || list.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= rawTotal) break;
    }
    const hit = all.filter((r) => selfKeywordHit(r, kw)).slice(0, MERGE_MAX_ROWS);
    // total = 本地命中数（真实命中）；本站总数超过拉取上限时如实告知截断
    return { rows: hit, total: hit.length, truncated: rawTotal > all.length };
  }
  const rows: HallRow[] = [];
  let total = 0;
  for (let p = 1; rows.length < MERGE_MAX_ROWS; p += 1) {
    const res = await fetchSelfRows(params, p, MERGE_PAGE_SIZE);
    total = res.total ?? total;
    const list = res.list || [];
    rows.push(...list);
    if (!list.length || list.length < MERGE_PAGE_SIZE || p * MERGE_PAGE_SIZE >= total) break;
  }
  return { rows: rows.slice(0, MERGE_MAX_ROWS), total: Math.max(total, rows.length) };
}

/**
 * 默认视图（外部 + 本站）跨源分页的抓取计划（纯函数，单测覆盖）。
 *
 * 原理：两源各自按发布时间倒序。合并集里第 i 条要么是本站第 j 条、要么是外部第 (i-?) 条；
 * 要让当前页 [offset, offset+pageSize) 完整，外部只要取**从 offset−selfTotal 起**的
 * (selfTotal + pageSize) 条即可 —— 因为排在某条外部公告前面的本站公告最多 selfTotal 条。
 * 这样 total 仍是两源真实 total 之和，分页深度不受 200 行上限影响（不重蹈「中标公示」合并视图把
 * total 截成 200 的坑：大厅是主浏览路径，默认视图从 1637 条缩到 205 条是不可接受的）。
 */
export function planSelfWindow(
  pageNo: number,
  pageSize: number,
  selfTotal: number,
): { extLimit: number; extOffset: number } {
  const offset = (pageNo - 1) * pageSize;
  const extLimit = Math.min(MERGE_PAGE_SIZE, Math.max(pageSize, selfTotal + pageSize));
  return { extLimit, extOffset: Math.max(0, offset - selfTotal) };
}

/** 外部公告的一次页式抓取（接口按 pageNo/pageSize 取数，不是偏移式） */
export interface ExtFetchSlice {
  limit: number;
  /** 这次请求要取的第几页（pageNo） */
  pageNo: number;
  /** 首页在**外部公告全集**里的 0 基下标 */
  startIndex: number;
}

/**
 * 覆盖当前页所需的**外部公告抓取切片**（纯函数，单测覆盖）。
 *
 * 从 `floor(extOffset/extLimit)*extLimit` 起按页推进到 offset+pageSize 为止：取整会让起点前移，
 * 所以通常需要 1 次、极端情况 2 次请求 —— 关键是**不能只取一页就切片**，否则窗口尾部会缺行
 * （实测 case：pageSize=3、本站 2 条、第 3 页时只取一页会少 2 行）。
 */
export function planSelfFetchSlices(
  pageNo: number,
  pageSize: number,
  selfTotal: number,
): ExtFetchSlice[] {
  const { extOffset, extLimit } = planSelfWindow(pageNo, pageSize, selfTotal);
  const needEnd = (pageNo - 1) * pageSize + pageSize;
  const out: ExtFetchSlice[] = [];
  let start = Math.floor(extOffset / extLimit) * extLimit;
  // guard：正常 ≤2 次；上限 4 次防止参数异常时死循环
  for (let guard = 0; start < needEnd && guard < 4; guard += 1) {
    out.push({ limit: extLimit, pageNo: start / extLimit + 1, startIndex: start });
    start += extLimit;
  }
  return out;
}

/**
 * 在"本站全量 + 外部一段"上切出当前页（纯函数，单测覆盖）。
 *
 * @param extStartIndex extRows[0] 在**外部公告全集**里的 0 基下标（分页取整后的真实起点）
 */
export function sliceMergedWindow(
  selfRows: HallRow[],
  extRows: HallRow[],
  extStartIndex: number,
  pageNo: number,
  pageSize: number,
): HallRow[] {
  const merged = [...selfRows, ...extRows].sort(byPublishTimeDesc);
  const offset = (pageNo - 1) * pageSize;
  const from = Math.max(0, offset - extStartIndex);
  return merged.slice(from, from + pageSize);
}

/**
 * 大厅取数统一入口（外部 + 本站）。
 * 分支只有三条：来源站=本站 → 只列本站；默认视图 → 合并；其余 → 只列外部（界面提示筛选口径）。
 */
export async function fetchHallPage(
  params: NoticePageParams & { quick?: string },
): Promise<PageResult<HallRow> & { truncated?: boolean }> {
  const pageNo = params.pageNo ?? 1;
  const pageSize = Math.min(params.pageSize ?? 10, 100);
  // ① 来源站 = 本站：服务端分页直达（total 取本站真实条数，分页深度不受任何上限影响）
  if (params.sourceCode === SELF_SOURCE) {
    // 「中标公示」合并口径：本站侧是"候选类 + 结果类"两个数据集类型（外站侧是原始值分组），
    // 拆两次请求后合并 —— 与外部合并视图同上限口径（页脚按 truncated 提示"仅展示前 200 条"）。
    if (params.noticeTypeRaw === RESULT_TYPE_MERGE) {
      const groups = await Promise.all(
        SELF_RESULT_TYPES.map((t) => fetchSelfAllRowsByType(params, t)),
      );
      const rows = groups.flatMap((g) => g.rows);
      const total = groups.reduce((sum, g) => sum + g.total, 0);
      return mergeSelfRows(rows, total, pageNo, pageSize);
    }
    // 关键词/编号查询（显式选「本站」来源）：后端检索不含本站公告，改本地匹配 + 本地分页，
    // 否则用户按编号查本站公告会得到空结果（2026-10-02 验收缺陷）。
    if (cleanT(params.keyword)) {
      const self = await fetchSelfAllRows(params);
      return mergeSelfRows(self.rows, self.total, pageNo, pageSize);
    }
    const res = await fetchSelfRows(params, pageNo, pageSize);
    return { list: res.list, total: res.total ?? 0 };
  }
  // ② 带外部专属筛选：只列外部公告（界面明示筛选口径）
  if (!hallMergesSelf(params)) {
    const res = await fetchNoticePage(params);
    return {
      list: (res.list || []).map((r) => ({ ...r, sourceKind: 'EXTERNAL' as const })),
      total: res.total,
      truncated: res.truncated,
    };
  }
  // ④ 编号精确搜索（2026-10-02）：外部 fetchNoticePage 自带 codeSearch 维度拆请求；
  //   本站 fetchSelfAllRows 同样按维度**真实检索** public/list（编号过滤实证可命中本站公告）。
  //   两源命中行按发布时间倒序合并分页（上限 MERGE_MAX_ROWS，截断如实告知）。
  if (cleanT(params.codeSearch?.value)) {
    const [extRaw, self] = await Promise.all([fetchNoticePage(params), fetchSelfAllRows(params)]);
    const extRows = (extRaw.list || []).map((r) => ({ ...r, sourceKind: 'EXTERNAL' as const }));
    const merged = mergeSelfRows([...extRows, ...self.rows], (extRaw.total ?? 0) + self.total, pageNo, pageSize);
    return {
      ...merged,
      truncated: merged.truncated || extRaw.truncated === true || self.truncated === true,
    };
  }
  // ③ 默认视图：跨源真分页 —— total = 外部 total + 本站 total，当前页由两源窗口合并切片
  const self = await fetchSelfAllRows(params);
  const selfRows = self.rows;
  const slices = planSelfFetchSlices(pageNo, pageSize, self.total);
  const extRows: HallRow[] = [];
  let extTotal = 0;
  for (const slice of slices) {
    const res = await pageOnce({
      ...toQueryPlain(params),
      pageNo: String(slice.pageNo),
      pageSize: String(slice.limit),
    });
    const rows = res.list || [];
    extTotal = res.total ?? extTotal;
    extRows.push(...rows.map((r) => ({ ...r, sourceKind: 'EXTERNAL' as const })));
    if (rows.length < slice.limit) break; // 外部公告已到底
    if (extTotal && slice.startIndex + slice.limit >= extTotal) break;
  }
  const extStartIndex = slices.length ? slices[0]!.startIndex : 0;
  return {
    list: sliceMergedWindow(selfRows, extRows, extStartIndex, pageNo, pageSize),
    // total = 两源真实条数之和；本站自身超过 200 条时按上限参与合并（truncated 如实告知）
    total: extTotal + self.total,
    truncated: self.truncated ?? self.total > selfRows.length,
  };
}

/* ================= 关注 / 订阅域（notice-follow；需登录 + bidding:notice-external:query/update） =================
 * 实现口径核对（后端 NoticeFollowController / FollowServiceImpl，01 §3 / 02 §5）：
 *   - 关注/取消：POST {prefix}/notice-follow/follow | /unfollow  body=FollowReqVO(targetType,targetKey)；
 *     ANNOUNCEMENT 关注会建“我关注的公告”行（displayName 暂为 targetKey，扇出时快照标题）；
 *   - 我的关注：GET {prefix}/notice-follow/item/page?followType=&followKey=&pageNo=&pageSize=
 *     （筛选参数真名 followType，契约已勘误；可叠加 followKey 做公告级/详情按钮的精确过滤，N2②）；
 *   - 时间线：GET {prefix}/notice-follow/item/{itemId}/timeline（分页）；
 *   - 已读：POST {prefix}/notice-follow/item/{itemId}/read（幂等，清未读 + readFlag）。
 * 鉴权：大厅免登录；关注域需登录态 + 权限码（读=query / 写=update，见 01 §3）。浏览器联调期（48110
 *   直跑未注册网关 O1）复用 /bidding-api 代理并附加 Authorization/tenant-id 请求头（from useAccessStore）。
 */

const FOLLOW_API = `${biddingConfig.baseUrl}${biddingConfig.apiPrefix}/notice-follow`;

/** 登录态请求（附加 Authorization/tenant-id；无 token 时报“请先登录”） */
async function authedFetch<T>(
  url: string,
  init: { body?: unknown; method?: string; } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body !== undefined) {
    // JSON body 必须显式声明，否则 fetch 默认 text/plain，后端 @RequestBody 直接 415
    headers['Content-Type'] = 'application/json';
  }
  try {
    const { useAccessStore } = await import('@vben/stores');
    const accessStore = useAccessStore();
    // token 只在本 try 内使用（下方拼 Authorization）→ 就地 const，不再外层 let 再赋值
    // （原写法被 no-useless-assignment 判为"死初始化"：初始 null 一定会被覆盖且 catch 后不再读）
    const token = accessStore.accessToken;
    const tenantId = accessStore.tenantId;
    if (token) headers.Authorization = `Bearer ${token}`;
    if (tenantId !== null && tenantId !== undefined) headers['tenant-id'] = String(tenantId);
  } catch {
    /* stores 未就绪：按未登录处理，后端返回 code=401 统一提示 */
  }
  const res = await fetch(url, {
    method: init.method || 'GET',
    headers,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  let body: null | { code?: number; data?: T; msg?: string } = null;
  try {
    body = (await res.json()) as { code?: number; data?: T; msg?: string };
  } catch {
    /* 非 JSON 响应 */
  }
  if (!res.ok || (body && typeof body.code === 'number' && body.code !== 0)) {
    const code = body?.code ?? res.status;
    let msg =
      body?.msg ||
      (res.ok ? '' : `请求失败：HTTP ${res.status}`) ||
      `公告接口错误：code=${code}`;
    if (code === 401) msg = '请先登录后再使用关注/订阅功能';
    // 关注域是「仅登录」范式（isAuthenticated()，见 §33）：**不需要权限码**，也不应出现 403。
    // 原先这里提示"需要 bidding:notice-external:query/update，请开通"会误导用户去申请一个用不到的权限码；
    // 真出现 403 时按系统/配置问题如实说明。
    if (code === 403) msg = '该操作暂不可用（服务端未放行，属系统配置问题，请联系管理员）';
    throw new ApiError(msg, code as number);
  }
  return (body ? body.data : undefined) as T;
}

function followQs(params: Record<string, unknown>): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  return qs.toString();
}

/** 关注（幂等） */
export async function followTarget(req: FollowReq): Promise<void> {
  if (biddingConfig.useMock) {
    mockToggleFollow('follow', req.targetType, req.targetKey);
    return;
  }
  await authedFetch<boolean>(`${FOLLOW_API}/follow`, { method: 'POST', body: req });
}

/** 取消关注（幂等） */
export async function unfollowTarget(req: FollowReq): Promise<void> {
  if (biddingConfig.useMock) {
    mockToggleFollow('unfollow', req.targetType, req.targetKey);
    return;
  }
  await authedFetch<boolean>(`${FOLLOW_API}/unfollow`, { method: 'POST', body: req });
}

/** 我的关注列表（item/page；followType 空=全部，可 ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION） */
export async function fetchMyFollowItems(
  params: FollowItemPageParams,
): Promise<PageResult<FollowItem>> {
  const data = biddingConfig.useMock
    ? await mockMyFollow(params)
    : await authedFetch<PageResult<FollowItem>>(
        `${FOLLOW_API}/item/page?${followQs({ ...params })}`,
      );
  return {
    total: data.total,
    list: (data.list || []).map((r) => ({
      ...r,
      displayName: cleanT(r.displayName),
      issuerName: r.issuerName ? cleanT(r.issuerName) : undefined,
    })),
  };
}

/** 关注项时间线（公告列表+动态） */
export async function fetchFollowTimeline(
  itemId: number | string,
  params: { pageNo: number; pageSize: number },
): Promise<PageResult<FollowEvent>> {
  const data = biddingConfig.useMock
    ? await mockTimeline(itemId, params)
    : await authedFetch<PageResult<FollowEvent>>(
        `${FOLLOW_API}/item/${encodeURIComponent(String(itemId))}/timeline?${followQs({ ...params })}`,
      );
  return {
    total: data.total,
    list: (data.list || []).map((e) => ({
      ...e,
      titleSnapshot: cleanT(e.titleSnapshot),
      origUrl: e.origUrl ? cleanT(e.origUrl) : undefined,
    })),
  };
}

/** 标记关注项已读（清未读 + 事件 readFlag） */
export async function markFollowItemRead(itemId: number | string): Promise<void> {
  if (biddingConfig.useMock) {
    mockReadItem(itemId);
    return;
  }
  await authedFetch<boolean>(
    `${FOLLOW_API}/item/${encodeURIComponent(String(itemId))}/read`,
    { method: 'POST' },
  );
}

/**
 * 关注状态单点查询（BR-2 / N2② 新增：GET /notice-follow/status）。
 * 返回 FollowStatusRespVO {followed, itemId, targetType, targetKey, displayName, issuerName}；
 * 公告级目标已关注且行缺快照时，后端即时按公告详情补名返回（不落库）。
 */
export async function fetchFollowStatus(
  targetType: string,
  targetKey: string,
): Promise<FollowStatus> {
  if (biddingConfig.useMock) return mockFollowStatus(targetType, targetKey);
  const data = await authedFetch<FollowStatus>(
    `${FOLLOW_API}/status?${followQs({ targetType, targetKey })}`,
  );
  return {
    ...data,
    displayName: cleanT(data.displayName),
    issuerName: data.issuerName ? cleanT(data.issuerName) : undefined,
  };
}

/** 是否已关注某目标（详情页关注按钮初始态；ANNOUNCEMENT/PROJECT/TENDER_PROJECT/SECTION 四维统一走 status 端点） */
export async function isFollowed(targetType: string, targetKey: string): Promise<boolean> {
  if (biddingConfig.useMock) return mockFollowed(targetType, targetKey);
  const st = await fetchFollowStatus(targetType, targetKey);
  return !!st?.followed;
}

/* ================= 内部公告（招标人自营公开公告；notice /public 域） =================
 * 对应后端 /bidding/notice：public/list + public/detail（@PermitAll，仅 PUBLISHED + 社会公开 SOCIAL）。
 * 行/详情 VO：NoticeRespVO / NoticeDetailRespVO（payload=权威数据集字段全集、content=正文 vN）。
 */
const INTERNAL_API = `${biddingConfig.baseUrl}${biddingConfig.apiPrefix}/notice`;

function normInternalRow(r: InternalNoticeItem): InternalNoticeItem {
  return { ...r, title: cleanT(r.title) || r.title, publisherName: r.publisherName ? cleanT(r.publisherName) : undefined };
}

/**
 * 流程行 / 关注项「本站 / 外站」来源判定（2026-10-02 用户口径：**本身是用表做区分的**）。
 *   · 本站公告在 bidding_notice（本平台发布），外站公告在 bidding_notice_external（外部采集）；
 *   · 前端按编号反查**外部公告公开分页**（notice-external/page，@PermitAll，支持 bidSectionCode/projectCode
 *     编号精确过滤）：**命中 = 外站（EXTERNAL）**；外部表查不到 = 本站（INTERNAL，本站公告不在外部表）。
 *   · 顺序：先按标段编号查，未命中再按项目编号查；两维都失败按本站兜底（本站公告不在外部表，
 *     而流程行编号要么来自本站项目、要么来自外部采集公告 —— 不会同时存在于两张表）。
 * 单测覆盖：index.source.test.ts。
 */
export async function probeNoticeSource(code: string): Promise<'EXTERNAL' | 'INTERNAL' | null> {
  const c = cleanT(code);
  if (!c) return null;
  const attempts: NoticePageParams[] = [
    { pageNo: 1, pageSize: 1, bidSectionCode: c },
    { pageNo: 1, pageSize: 1, projectCode: c },
  ];
  for (const p of attempts) {
    try {
      const page = await fetchNoticePage(p);
      if ((page.list ?? []).length > 0) return 'EXTERNAL';
    } catch {
      // 单次查询失败（网络/网关抖动）：继续下一维度，不要因为一次失败把整行判成未知
    }
  }
  return 'INTERNAL';
}

/** 招标人发布公告公开分页（大厅“本平台发布”目录） */
export async function fetchInternalNotices(
  params: InternalNoticeParams,
): Promise<PageResult<InternalNoticeItem>> {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
  });
  const data = biddingConfig.useMock
    ? await mockInternalPage({ ...params })
    : await getJson<PageResult<InternalNoticeItem>>(`${INTERNAL_API}/public/list?${qs.toString()}`);
  return { total: data.total, list: (data.list || []).map(normInternalRow) };
}

/** 招标人发布公告公开详情（payload 结构化字段 + content 正文 + PDF 元数据） */
export async function fetchInternalNoticeDetail(
  id: number | string,
): Promise<InternalNoticeDetail> {
  if (biddingConfig.useMock) {
    const hit = mockInternalDetail(String(id));
    if (!hit) throw new Error('公告不存在或已下架');
    return hit;
  }
  let data: InternalNoticeDetail;
  try {
    data = await getJson<InternalNoticeDetail>(
      `${INTERNAL_API}/public/detail?id=${encodeURIComponent(String(id))}`,
    );
  } catch (e) {
    if (e instanceof ApiError && (e.code === CODE_NOT_EXISTS || e.code === CODE_TAKEN_DOWN)) {
      throw new Error('公告不存在或已下架');
    }
    throw e;
  }
  return normInternalRow(data) as InternalNoticeDetail;
}

