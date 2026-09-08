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
import type {
  FollowEvent,
  FollowItem,
  FollowItemPageParams,
  FollowStatus,
  FollowReq,
  InternalNoticeDetail,
  InternalNoticeItem,
  InternalNoticeParams,
  NoticeBodyBlock,
  NoticeExternalDetail,
  NoticeExternalItem,
  NoticePageParams,
  PageResult,
} from './types';

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
  const map: Record<string, number> = { 近7天: 7, 近1个月: 30, 近3个月: 90 };
  const days = map[quick];
  if (!days) return undefined;
  const t = new Date(Date.now() - days * 24 * 60 * 60 * 1000);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${t.getFullYear()}-${p(t.getMonth() + 1)}-${p(t.getDate())} 00:00:00`;
}

const DATE_ONLY_RE = /^\d{4}-\d{2}-\d{2}$/;
/** 日期/时间串 → 接口可解析的 'yyyy-MM-dd HH:mm:ss'（仅日期自动补起止时刻） */
function normTime(v: string | number | undefined, isEnd: boolean): string | undefined {
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

/** 合并视图抓取（“中标公示”= 候选类 + 结果类；后端 eq 单值，前端分组抓取合并） */
async function fetchMerged(
  params: NoticePageParams & { quick?: string },
): Promise<PageResult<NoticeExternalItem>> {
  const pageNo = params.pageNo ?? 1;
  const pageSize = Math.min(params.pageSize ?? 10, 100);
  const raws: string[] = (Array.isArray(params.resultRaws) ? params.resultRaws : []).filter(Boolean);
  if (!raws.length) return { list: [], total: 0 };

  const base = toQueryPlain({ ...params, noticeTypeRaw: undefined });
  // 合并视图按组逐原始值抓全（分页上限 100/页；行数上限 200，对齐 keyword 命中集上限 01 §1.1）
  const MAX_ROWS = 200;
  const acc = new Map<string, NoticeExternalItem>();
  outer: for (const raw of raws) {
    for (let p = 1; ; p += 1) {
      const plain: Record<string, string> = {
        ...base,
        noticeTypeRaw: raw,
        pageNo: String(p),
        pageSize: '100',
      };
      const res = await pageOnce(plain);
      const rows = res.list || [];
      for (const r of rows) {
        if (!acc.has(String(r.id))) acc.set(String(r.id), r);
      }
      if (acc.size >= MAX_ROWS) break outer;
      if (!res.total || rows.length < 100 || p * 100 >= res.total) break;
    }
  }
  const all = [...acc.values()].sort((a, b) => {
    const pa = a.publishTime ?? 0;
    const pb = b.publishTime ?? 0;
    if (typeof pa === 'number' && typeof pb === 'number') return pb - pa;
    const sa = String(pa);
    const sb = String(pb);
    if (sa === sb) return 0;
    return sa < sb ? 1 : -1; // 字符串时间格式可字典序倒排
  });
  const start = (pageNo - 1) * pageSize;
  // truncated：合并视图命中 MAX_ROWS 上限（页脚据此提示"仅展示前 200 条"，避免 total 失真无感知）
  return {
    total: all.length,
    list: all.slice(start, start + pageSize),
    truncated: all.length >= MAX_ROWS,
  } as PageResult<NoticeExternalItem> & { truncated?: boolean };
}

/** 列表（大厅公告目录） */
export async function fetchNoticePage(
  params: NoticePageParams & { quick?: string },
): Promise<PageResult<NoticeExternalItem>> {
  try {
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
  rawJson: string | null | undefined,
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
        ['heading', 'paragraph', 'table', 'list'].includes(b.type),
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
export function openOrigUrl(origUrl?: string | null): void {
  if (!origUrl) return;
  if (biddingConfig.useMock) {
    // Mock 态无 go 服务，直接打开原地址便于演示
    window.open(origUrl, '_blank', 'noopener,noreferrer');
    return;
  }
  const go = `${API}/go?url=${encodeURIComponent(origUrl)}`;
  window.open(go, '_blank', 'noopener,noreferrer');
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
  init: { method?: string; body?: unknown } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (init.body !== undefined) {
    // JSON body 必须显式声明，否则 fetch 默认 text/plain，后端 @RequestBody 直接 415
    headers['Content-Type'] = 'application/json';
  }
  let token: string | null = null;
  try {
    const { useAccessStore } = await import('@vben/stores');
    const accessStore = useAccessStore();
    token = accessStore.accessToken;
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
  let body: { code?: number; data?: T; msg?: string } | null = null;
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
    if (code === 403) msg = '无操作权限（需要 bidding:notice-external:query/update），请联系管理员开通';
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

