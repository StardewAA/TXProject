/**
 * 仅前端 mock 演示数据，联调后移除；字段真源以 NoticeDetailRespVO / ExternalNoticeVO 为准
 * （内部公告详情=NoticeDetailRespVO；外部公告/关注域=后端 NoticeExternal* / Follow* 系列 VO，
 * 口径另见 docs/外部公告-前端对接/01-接口契约.md）。
 */
/**
 * 内置 Mock（后端 notice-external 未部署到联调环境时使用；VITE_BIDDING_USE_MOCK=1 开启）。
 *
 * 数据结构与 01-接口契约.md / 后端 VO 一致（列表行已带 sourceCode/legalFlag，正文含结构化块演示）；
 * 过滤语义与真实接口对齐：keyword 为包含检索（模拟 ES）、noticeTypeRaw/sourceCode/contentType/
 * industryFlag 均精确匹配、beginTime/endTime 取发布时间区间；真实联调后由 api.ts 直连后端。
 * 注意：Mock 演示数据非后端真实内容（页面有“演示 Mock 数据”标识）。
 */
import type {
  FollowEvent,
  FollowItem,
  FollowItemPageParams,
  FollowStatus,
  InternalNoticeDetail,
  InternalNoticeItem,
  NoticeBodyBlock,
  NoticeExternalDetail,
  NoticeExternalItem,
  PageResult,
} from './types';

const DAY = 24 * 60 * 60 * 1000;
const now = Date.now();
const p2 = (n: number) => String(n).padStart(2, '0');
const iso = (d: number) => {
  const t = new Date(d);
  return `${t.getFullYear()}-${p2(t.getMonth() + 1)}-${p2(t.getDate())} ${p2(t.getHours())}:${p2(t.getMinutes())}`;
};
const d = (agoDays: number, h = 9, m = 0) => iso(now - agoDays * DAY + h * 3600000 + m * 60000);
/** 字符串时间（YYYY-MM-DD HH:mm[:ss]）→ ms，便于区间比较 */
function toMs(v: string): number {
  const s = /^\d{4}-\d{2}-\d{2}( \d{2}:\d{2}(:\d{2})?)?$/.test(v) ? v.replace(' ', 'T') : v;
  const t = new Date(s);
  return Number.isNaN(+t) ? 0 : +t;
}

const list: NoticeExternalItem[] = [
  {
    id: 1001,
    sourceCode: 'EX-TRADE',
    sourceName: '公共资源交易中心',
    title: '平陆县集中供热管网改造工程（一标段）防腐保温材料采购招标公告',
    origUrl: 'https://example.com/notice/1001',
    contentType: 'HTML',
    noticeTypeRaw: '招标公告',
    legalFlag: 'Y',
    industryFlag: 'BELONGS',
    issuerName: '平陆县住房和城乡建设局',
    publishTime: d(2, 9, 30),
    effectiveFrom: iso(now - 2 * DAY),
    effectiveTo: iso(now - 2 * DAY + 21 * DAY),
    status: 'ACTIVE',
  },
  {
    id: 1002,
    sourceCode: 'EX-TRADE',
    sourceName: '公共资源交易中心',
    title: '太原市热力集团 2026 年度一次网保温管件采购项目中标候选人公示',
    origUrl: 'https://example.com/notice/1002',
    contentType: 'HTML',
    noticeTypeRaw: '中标候选人公示',
    legalFlag: 'Y',
    industryFlag: 'BELONGS',
    issuerName: '太原市热力集团',
    publishTime: d(4, 16, 20),
    effectiveFrom: iso(now - 4 * DAY),
    effectiveTo: iso(now + 2 * DAY),
    status: 'ACTIVE',
  },
  {
    id: 1003,
    sourceCode: 'EX-TRADE',
    sourceName: '公共资源交易中心',
    title: '太原市热力集团 2026 年度一次网保温管件采购项目中标结果公示',
    origUrl: 'https://example.com/notice/1003',
    contentType: 'HTML',
    noticeTypeRaw: '中标结果公示',
    legalFlag: 'Y',
    industryFlag: 'BELONGS',
    issuerName: '太原市热力集团',
    publishTime: d(6, 10, 5),
    effectiveFrom: iso(now - 6 * DAY),
    effectiveTo: iso(now + 2 * DAY),
    status: 'ACTIVE',
  },
  {
    id: 1004,
    sourceCode: 'SAMPLE_HUB',
    sourceName: '示例交易平台',
    title: '某石化项目低温乙烯储罐保冷工程（2#罐）变更公告',
    origUrl: 'https://example.com/notice/1004',
    contentType: 'HTML',
    noticeTypeRaw: '变更公告',
    legalFlag: 'Y',
    industryFlag: 'UNCERTAIN',
    issuerName: '某石化工程公司',
    publishTime: d(9, 11, 5),
    status: 'ACTIVE',
  },
  {
    id: 1005,
    sourceCode: 'SAMPLE_HUB',
    sourceName: '示例交易平台',
    title: '晋城市新区市政供热配套项目监理招标（重新招标）',
    origUrl: 'https://example.com/notice/1005',
    contentType: 'ATTACHMENT',
    noticeTypeRaw: '招标公告',
    issuerName: '晋城新区管委会',
    publishTime: d(12, 14, 45),
    status: 'ACTIVE',
  },
  {
    id: 1006,
    sourceCode: 'EBIDDING',
    sourceName: '秦源招标电子招标平台',
    title: '平陆县集中供热管网改造工程（二标段）材料采购资格预审公告',
    origUrl: 'https://www.ebidding.com.cn/notice/detail?doc=MOCK-1006',
    contentType: 'PDF',
    noticeTypeRaw: '资格预审公告',
    industryFlag: 'UNCERTAIN',
    issuerName: '平陆县住房和城乡建设局',
    publishTime: d(15, 8, 40),
    status: 'ACTIVE',
  },
];

const HTML_BODY = `
  <h2 style="text-align:center">平陆县集中供热管网改造工程（一标段）防腐保温材料采购招标公告</h2>
  <h3>一、项目概况与招标范围</h3>
  <p>本招标项目平陆县集中供热管网改造工程（一标段）已由平发改审〔2026〕××号文件批准建设，项目业主为平陆县住房和城乡建设局，建设资金来自财政资金，项目出资比例为百分之百。项目已具备招标条件，现对该项目防腐保温材料采购与施工进行公开招标。</p>
  <h3>二、标段划分</h3>
  <table><tr><th>标段</th><th>工程内容</th><th>工期</th><th>质保期</th></tr>
  <tr><td>一标段</td><td>一次网管道防腐保温采购与施工</td><td>90 日历天</td><td>2 年</td></tr></table>
  <h3>三、投标人资格要求</h3>
  <p>（一）投标人须具备防腐保温工程专业承包资质，并在人员、设备、资金等方面具有相应的施工能力；</p>
  <p>（二）本次招标不接受联合体投标。</p>`;

/** 结构化正文块演示（schema：02 §4 heading/paragraph/table/list） */
const CANDIDATE_BLOCKS: NoticeBodyBlock[] = [
  { type: 'heading', level: 2, text: '一、评标结果' },
  {
    type: 'paragraph',
    text: '本项目评标工作已结束，经评标委员会评审，现将中标候选人予以公示，公示期三日。',
  },
  {
    type: 'table',
    headers: ['名次', '中标候选人名称', '投标报价（元）', '项目经理'],
    rows: [
      ['第一', '××保温材料有限公司', '3,268,500.00', '×××'],
      ['第二', '××管业科技有限公司', '3,310,000.00', '×××'],
    ],
  },
  { type: 'list', ordered: true, items: ['公示期间如对评标结果有异议，请向招标人提出书面质疑。'] },
];

const details: Record<string, NoticeExternalDetail> = {
  1001: {
    ...list[0]!,
    projectName: '平陆县集中供热管网改造工程（一标段）',
    projectKey: 'PRJ-PL-2026',
    bidSectionCodes: ['BYB-001'],
    bidSectionKeys: ['BYB-001'],
    bidSectionNames: ['BYB-001 · 一标段（防腐保温材料采购与施工）'],
    contentHtmlSafe: HTML_BODY,
    bodyBlocksStatus: 'NONE',
    attachments: [
      { id: 501, name: '招标文件.pdf', ext: 'pdf', size: 2.4 * 1024 * 1024, virusStatus: 'CLEAN' },
      { id: 502, name: '图纸清单.dwg', ext: 'dwg', size: 18.1 * 1024 * 1024, virusStatus: 'CLEAN' },
      { id: 503, name: '补遗澄清-01.pdf', ext: 'pdf', size: 0.6 * 1024 * 1024, virusStatus: 'PENDING' },
    ],
  },
  1002: {
    ...list[1]!,
    projectName: '太原市热力集团 2026 年度一次网保温管件采购项目',
    projectKey: 'PRJ-TY-TR-2026',
    bidSectionCodes: ['TR-2026-01'],
    bidSectionKeys: ['TR-2026-01'],
    bidSectionNames: ['TR-2026-01 · 保温管件采购一标段'],
    contentHtmlSafe: `<h3>一、评标结果</h3><p>本项目评标工作已结束，经评标委员会评审，现将中标候选人予以公示。</p>
      <table><tr><th>名次</th><th>中标候选人名称</th><th>投标报价（元）</th></tr>
      <tr><td>第一</td><td>××保温材料有限公司</td><td>3,268,500.00</td></tr></table>`,
    bodyParserCode: 'HTML_STRUCTURE',
    bodyBlocksStatus: 'SUCCESS',
    bodyBlocks: CANDIDATE_BLOCKS,
    attachments: [],
  },
  1003: {
    ...list[2]!,
    projectName: '太原市热力集团 2026 年度一次网保温管件采购项目',
    projectKey: 'PRJ-TY-TR-2026',
    contentHtmlSafe: '<h3>中标结果</h3><p>××保温材料有限公司为本项目中标人，中标价 3,268,500.00 元。</p>',
    attachments: [],
  },
  1004: {
    ...list[3]!,
    contentHtmlSafe: '<h3>更正内容</h3><p>原招标文件第六章评标办法第 × 条内容更正如下：……其余事项不变。</p>',
    attachments: [],
  },
  1005: {
    ...list[4]!,
    // 无正文 → “仅简略信息”形态（附件另配）
    contentHtmlSafe: '',
    attachments: [
      { id: 601, name: '监理招标文件.pdf', ext: 'pdf', size: 1.1 * 1024 * 1024, virusStatus: 'CLEAN' },
    ],
  },
  1006: {
    ...list[5]!,
    pdfFileId: 601,
    pdfSha256: 'a'.repeat(64),
    pdfSize: 3.1 * 1024 * 1024,
    pdfDownloadUrl: '',
    extractionStatus: 'SUCCESS',
    contentHtmlSafe: '',
    attachments: [],
  },
};

function quickStart(quick?: string): string | undefined {
  if (!quick || quick === '全部时间') return undefined;
  /* 与 api.ts 的 quickToBegin **同口径**（本函数是 Mock 版实现，此前两处不同步）：
   *   · 标签逐字一致 —— 2026-09-14 界面统一为「最近 7 天 / 最近 30 天 / 最近 90 天」后，旧键名会让 Mock 全部落到兜底值；
   *   · 「今年以来」= 自然年 1 月 1 日（旧版按 365 天回溯 = "去年今天"，真实实现已修并注释说明，Mock 此前没跟上）；
   *   · 未识别标签返回 undefined（旧版兜底成 7 天，会静默改变筛选语义）。 */
  if (quick === '今年以来') return iso(new Date(new Date().getFullYear(), 0, 1).getTime());
  const map: Record<string, number> = { '最近 7 天': 7, '最近 30 天': 30, '最近 90 天': 90 };
  const days = map[quick];
  if (!days) return undefined;
  return iso(now - days * DAY);
}

/**
 * Mock 分页（语义与真实 page 一致：keyword 包含检索；其余精确 eq；publishTime 区间）。
 * 入参为规范化后的查询键（api.ts 已做界面文案→值域映射）。
 */
export function mockPage(params: Record<string, unknown>): PageResult<NoticeExternalItem> {
  const fromStr = (params.beginTime as string) || quickStart(params.quick as string | undefined);
  const toStr = (params.endTime as string) || undefined;
  const fromMs = fromStr ? toMs(fromStr) : undefined;
  const toMsEnd = toStr ? toMs(/^\d{4}-\d{2}-\d{2}$/.test(toStr) ? `${toStr} 23:59:59` : toStr) : undefined;

  let rows = [...list];
  const kw = String(params.keyword ?? '').trim().toLowerCase();
  if (kw) {
    rows = rows.filter((r) =>
      [r.title, r.issuerName, r.sourceName, r.noticeTypeRaw, r.sourceCode]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(kw),
    );
  }
  const title = String(params.title ?? '');
  if (title) rows = rows.filter((r) => r.title === title);
  const src = String(params.sourceCode ?? '');
  if (src) rows = rows.filter((r) => r.sourceCode === src);
  const ct = String(params.contentType ?? '');
  if (ct) rows = rows.filter((r) => r.contentType === ct);
  const ind = String(params.industryFlag ?? '');
  if (ind) rows = rows.filter((r) => r.industryFlag === ind);
  const raw = String(params.noticeTypeRaw ?? '');
  if (raw) rows = rows.filter((r) => r.noticeTypeRaw === raw);
  if (fromMs !== undefined) rows = rows.filter((r) => toMs(String(r.publishTime)) >= fromMs);
  if (toMsEnd !== undefined) rows = rows.filter((r) => toMs(String(r.publishTime)) <= toMsEnd);

  rows.sort((a, b) => toMs(String(b.publishTime)) - toMs(String(a.publishTime)));
  const pageNo = Math.max(1, Number(params.pageNo ?? 1) || 1);
  const pageSize = Math.min(100, Math.max(1, Number(params.pageSize ?? 10) || 10));
  const start = (pageNo - 1) * pageSize;
  return { list: rows.slice(start, start + pageSize), total: rows.length };
}

export function mockDetail(id: string): NoticeExternalDetail | undefined {
  const hit = details[id];
  if (!hit) return undefined;
  return {
    ...hit,
    attachments: (hit.attachments ?? []).map((a) => ({ ...a })),
    bodyBlocks: Array.isArray(hit.bodyBlocks) ? hit.bodyBlocks.map((b) => ({ ...b })) : undefined,
  };
}

/* ================= 关注/订阅域 Mock（内存态；与真实 notice-follow 语义对齐） =================
 * - follow/unfollow 幂等：FOLLOWING 行出列表；unfollow 置 UNFOLLOWED 移出；重 follow 恢复并回最新动态；
 * - 公告级关注：targetType=ANNOUNCEMENT，targetKey=公告 id；行 displayName 按详情快照标题（真实后端
 *   暂存 targetKey、扇出时才快照 —— 前端在 api 层用 detail 回查补名，N2 见 docs 说明）；
 * - 时间线：预置关注行的历史事件（EXTERNAL_ACTIVE = 外部公告审核生效提醒）；read 清未读。
 */

const DAY_M = 24 * 60 * 60 * 1000;

interface MockFollowRec extends FollowItem {
  followStatus: 'FOLLOWING' | 'UNFOLLOWED';
}

function followSeedRows(): MockFollowRec[] {
  const nowM = now;
  return [
    {
      id: 8001,
      followType: 'TENDER_PROJECT',
      followKey: 'TP-2026-LH-DYZB',
      displayName: '炼化公司所属各单位2026年装置大修消缺公开招标',
      issuerName: '陕西延长石油（集团）有限责任公司炼化公司',
      stageCode: 'EVAL',
      stageName: '评标',
      unreadCount: 2,
      followStatus: 'FOLLOWING',
      followTime: nowM - 30 * DAY_M,
      latestDynamicTime: nowM - 2 * DAY_M,
    },
    {
      id: 8002,
      followType: 'PROJECT',
      followKey: 'PRJ-TY-TR-2026',
      displayName: '太原市热力集团 2026 年度一次网保温管件采购项目',
      issuerName: '太原市热力集团',
      stageCode: 'RESULT',
      stageName: '中标结果',
      unreadCount: 1,
      followStatus: 'FOLLOWING',
      followTime: nowM - 20 * DAY_M,
      latestDynamicTime: nowM - 6 * DAY_M,
    },
    {
      id: 8003,
      followType: 'ANNOUNCEMENT',
      followKey: '1002',
      displayName: '太原市热力集团 2026 年度一次网保温管件采购项目中标候选人公示',
      issuerName: '太原市热力集团',
      stageCode: 'UNKNOWN',
      stageName: '',
      unreadCount: 1,
      followStatus: 'FOLLOWING',
      followTime: nowM - 4 * DAY_M,
      latestDynamicTime: nowM - 4 * DAY_M,
      noticeStatus: 'ACTIVE',
      available: true,
    },
    {
      id: 8004,
      followType: 'SECTION',
      followKey: 'BYB-OLD-001',
      displayName: 'BYB-OLD-001 · 一标段（示例：关联公告已下线）',
      issuerName: '平陆县住房和城乡建设局',
      stageCode: 'TENDER',
      stageName: '招标',
      unreadCount: 0,
      followStatus: 'FOLLOWING',
      followTime: nowM - 2 * DAY_M,
      latestDynamicTime: nowM - 2 * DAY_M,
    },
    {
      id: 8005,
      followType: 'PROJECT',
      followKey: 'PRJ-DROPPED',
      displayName: '已取消关注的示例项目（不应出现在列表）',
      issuerName: '',
      stageCode: 'UNKNOWN',
      stageName: '',
      unreadCount: 0,
      followStatus: 'UNFOLLOWED',
      followTime: nowM - 40 * DAY_M,
      latestDynamicTime: null,
    },
  ];
}

interface MockTimelineRec extends FollowEvent {
  itemId: number | string;
}

function seedTimeline(): MockTimelineRec[] {
  const nowM = now;
  return [
    // 8001 炼化大修招标项目：已读 STAGE_CHANGE + 2 条未读 EXTERNAL_ACTIVE
    { id: 9001, itemId: 8001, source: 'INTERNAL', eventType: 'STAGE_CHANGE', titleSnapshot: '项目阶段流转：招标 → 评标', occurredAt: nowM - 3 * DAY_M, readFlag: true },
    { id: 9002, itemId: 8001, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1002, titleSnapshot: '太原市热力集团 2026 年度一次网保温管件采购项目中标候选人公示（模拟外部平台）', origUrl: 'https://example.com/notice/1002', occurredAt: nowM - 2 * DAY_M, readFlag: false, noticeStatus: 'ACTIVE', available: true },
    { id: 9003, itemId: 8001, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1003, titleSnapshot: '太原市热力集团 2026 年度一次网保温管件采购项目中标结果公示（模拟外部平台）', origUrl: 'https://example.com/notice/1003', occurredAt: nowM - 1 * DAY_M, readFlag: false, noticeStatus: 'ACTIVE', available: true },
    // 8002 太原项目
    { id: 9004, itemId: 8002, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1003, titleSnapshot: '太原市热力集团 2026 年度一次网保温管件采购项目中标结果公示', origUrl: 'https://example.com/notice/1003', occurredAt: nowM - 6 * DAY_M, readFlag: false, noticeStatus: 'ACTIVE', available: true },
    { id: 9005, itemId: 8002, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1002, titleSnapshot: '太原市热力集团 2026 年度一次网保温管件采购项目中标候选人公示', origUrl: 'https://example.com/notice/1002', occurredAt: nowM - 6 * DAY_M + 3600_000, readFlag: true, noticeStatus: 'ACTIVE', available: true },
    // 8003 公告级关注（太原中标候选人公示）
    { id: 9006, itemId: 8003, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1002, titleSnapshot: '太原市热力集团 2026 年度一次网保温管件采购项目中标候选人公示', origUrl: 'https://example.com/notice/1002', occurredAt: nowM - 4 * DAY_M, readFlag: false, noticeStatus: 'ACTIVE', available: true },
    // 8004 标段（平陆一标段招标公告）→ 关联公告已下线（BR-4 演示：平台详情不可达，仅源站原文）
    { id: 9007, itemId: 8004, source: 'EXTERNAL', eventType: 'EXTERNAL_ACTIVE', noticeId: 1001, titleSnapshot: '平陆县集中供热管网改造工程（一标段）防腐保温材料采购招标公告', origUrl: 'https://example.com/notice/1001', occurredAt: nowM - 2 * DAY_M, readFlag: true, noticeStatus: 'INACTIVE', available: false },
  ];
}

/** 关注存储（模块级单例：跨页面（详情关注 ↔ 我的关注列表）共享状态） */
const followStore: { events: MockTimelineRec[]; rows: MockFollowRec[]; } = (() => {
  const saved = (globalThis as Record<string, unknown>).__bidding_mock_follow;
  if (saved) return saved as { events: MockTimelineRec[]; rows: MockFollowRec[]; };
  const store = { rows: followSeedRows(), events: seedTimeline() };
  (globalThis as Record<string, unknown>).__bidding_mock_follow = store;
  return store;
})();

const toFollowVo = (r: MockFollowRec): FollowItem => ({
  id: r.id,
  followType: r.followType,
  followKey: r.followKey,
  displayName: r.displayName,
  issuerName: r.issuerName,
  stageCode: r.stageCode,
  stageName: r.stageName,
  latestDynamicTime: r.latestDynamicTime,
  unreadCount: r.unreadCount,
  followTime: r.followTime,
  noticeStatus: r.noticeStatus,
  available: r.available,
});

/** 关注/取消（幂等）；公告级关注新建行时按 mock 详情快照标题/发布单位 */
export function mockToggleFollow(
  action: 'follow' | 'unfollow',
  targetType: string,
  targetKey: string,
): void {
  const store = followStore;
  const found = store.rows.find(
    (r) => r.followType === targetType && r.followKey === targetKey,
  );
  if (action === 'unfollow') {
    if (found) {
      found.followStatus = 'UNFOLLOWED';
    }
    return;
  }
  if (found) {
    if (found.followStatus !== 'FOLLOWING') {
      found.followStatus = 'FOLLOWING';
      found.followTime = now;
    }
    return; // 已关注：幂等
  }
  let displayName = targetKey;
  let issuerName = '';
  if (targetType === 'ANNOUNCEMENT') {
    const det = details[String(targetKey)];
    if (det) {
      displayName = det.title || targetKey;
      issuerName = det.issuerName || '';
    }
  } else if (targetType === 'SECTION') {
    const det = Object.values(details).find(
      (d) =>
        (Array.isArray(d.bidSectionCodes) && d.bidSectionCodes.includes(String(targetKey))) ||
        (Array.isArray(d.bidSectionKeys) && d.bidSectionKeys.includes(String(targetKey))),
    );
    if (det) {
      let idx = Array.isArray(det.bidSectionCodes) ? det.bidSectionCodes.indexOf(String(targetKey)) : -1;
      if (idx < 0 && Array.isArray(det.bidSectionKeys)) idx = det.bidSectionKeys.indexOf(String(targetKey));
      const nm = Array.isArray(det.bidSectionNames) && idx >= 0 ? det.bidSectionNames[idx] : '';
      displayName = (nm ? String(nm).trim() : '') || det.title || targetKey;
      issuerName = det.issuerName || '';
    }
  } else {
    const det = list.find((r) =>
      targetType === 'PROJECT'
        ? r.title?.includes(targetKey)
        : String(r.id) === targetKey || r.title?.includes(targetKey),
    );
    if (det) {
      displayName = det.title || targetKey;
      issuerName = det.issuerName || '';
    }
  }
  const maxId = store.rows.reduce((m, r) => Math.max(m, Number(r.id) || 0), 8000);
  const row: MockFollowRec = {
    id: maxId + 1,
    followType: targetType,
    followKey: targetKey,
    displayName,
    issuerName,
    stageCode: 'UNKNOWN',
    stageName: '',
    unreadCount: 0,
    followStatus: 'FOLLOWING',
    followTime: now,
    // BR-6（mock 对齐）：关注即种已读初始动态 → latestDynamicTime 置关注时刻（真实后端由扇出种行）
    latestDynamicTime: now,
    noticeStatus: targetType === 'ANNOUNCEMENT' && details[String(targetKey)] ? 'ACTIVE' : null,
    available: targetType === 'ANNOUNCEMENT' && details[String(targetKey)] ? true : null,
  };
  store.rows.push(row);
  // BR-6（mock 对齐真实后端扇出）：关注公告/标段即种一条已读 EXTERNAL_ACTIVE 初始动态
  // （timeline 立即有内容、latestDynamicTime=关注时刻；事件带公告关联，订阅页可直接跳详情）
  const rel = targetType === 'ANNOUNCEMENT'
    ? details[String(targetKey)]
    : targetType === 'SECTION'
      ? Object.values(details).find(
          (d) =>
            (Array.isArray(d.bidSectionCodes) && d.bidSectionCodes.includes(String(targetKey))) ||
            (Array.isArray(d.bidSectionKeys) && d.bidSectionKeys.includes(String(targetKey))),
        )
      : undefined;
  if (rel) {
    row.noticeStatus = 'ACTIVE';
    row.available = true;
    const maxEv = store.events.reduce((mx, e) => Math.max(mx, Number(e.id) || 0), 9000);
    store.events.push({
      id: maxEv + 1,
      itemId: row.id,
      source: 'EXTERNAL',
      eventType: 'EXTERNAL_ACTIVE',
      noticeId: String(rel.id),
      titleSnapshot: rel.title || displayName,
      origUrl: rel.origUrl || '',
      occurredAt: now,
      readFlag: true,
      noticeStatus: 'ACTIVE',
      available: true,
    });
  }
}

export function mockFollowed(targetType: string, targetKey: string): boolean {
  return followStore.rows.some(
    (r) =>
      r.followType === targetType &&
      r.followKey === String(targetKey) &&
      r.followStatus === 'FOLLOWING',
  );
}

/** 关注状态单点查询 Mock（BR-2/N2②：与真实 /notice-follow/status 同构出参） */
export function mockFollowStatus(targetType: string, targetKey: string): FollowStatus {
  const row = followStore.rows.find(
    (r) =>
      r.followType === targetType &&
      r.followKey === String(targetKey) &&
      r.followStatus === 'FOLLOWING',
  );
  return {
    followed: !!row,
    itemId: row?.id ?? null,
    targetType,
    targetKey: String(targetKey),
    displayName: row?.displayName ?? null,
    issuerName: row?.issuerName ?? null,
  };
}

/** 我的关注分页（只出 FOLLOWING；followType 过滤；按最近动态倒序） */
export function mockMyFollow(params: FollowItemPageParams): PageResult<FollowItem> {
  let rows = followStore.rows.filter((r) => r.followStatus === 'FOLLOWING');
  if (params.followType) {
    rows = rows.filter((r) => r.followType === params.followType);
  }
  rows.sort((a, b) => {
    const ta = typeof a.latestDynamicTime === 'number' ? a.latestDynamicTime : 0;
    const tb = typeof b.latestDynamicTime === 'number' ? b.latestDynamicTime : 0;
    return tb - ta;
  });
  const pageNo = Math.max(1, params.pageNo || 1);
  const pageSize = Math.min(100, params.pageSize || 10);
  const start = (pageNo - 1) * pageSize;
  return { total: rows.length, list: rows.slice(start, start + pageSize).map(toFollowVo) };
}

/** 时间线分页（倒序；itemId 归属校验后返回） */
export function mockTimeline(
  itemId: number | string,
  params: { pageNo: number; pageSize: number },
): PageResult<FollowEvent> {
  const rows = followStore.events
    .filter((e) => String(e.itemId) === String(itemId))
    .sort((a, b) => Number(b.occurredAt) - Number(a.occurredAt));
  const pageNo = Math.max(1, params.pageNo || 1);
  const pageSize = Math.min(100, params.pageSize || 10);
  const start = (pageNo - 1) * pageSize;
  return {
    total: rows.length,
    list: rows.slice(start, start + pageSize).map((e) => ({ ...e, itemId: undefined }) as FollowEvent),
  };
}

/** 标记关注项已读（清未读 + 该行事件 readFlag=true） */
export function mockReadItem(itemId: number | string): void {
  const key = String(itemId);
  const row = followStore.rows.find((r) => String(r.id) === key);
  if (row) row.unreadCount = 0;
  followStore.events.forEach((e) => {
    if (String(e.itemId) === key) e.readFlag = true;
  });
}

/* ================= 内部公告（招标人自营公开公告；notice /public 域 Mock） =================
 * 语义与后端 notice public/list（仅 PUBLISHED + 社会公开）一致：
 * publicLevel=SOCIAL 且非 internalFlag 才进入大厅；行含 noticeTypeName/contentPreview。
 */
const INTERNAL_TYPE_LABEL: Record<string, string> = {
  TENDER_NOTICE: '招标公告',
  CHANGE_NOTICE: '变更公告',
  WIN_CANDIDATE_PUBLICITY: '中标候选人公示',
  WIN_RESULT_PUBLICITY: '中标结果公示',
};
const internalList: InternalNoticeItem[] = [
  {
    id: 7001,
    noticeCode: 'BID-ENT-2026-0001',
    noticeType: 'TENDER_NOTICE',
    noticeTypeName: INTERNAL_TYPE_LABEL.TENDER_NOTICE,
    title: '榆林炼油厂 2026 年大修消缺项目防腐保温工程施工招标公告（本平台发布）',
    projectCode: 'DYZB2026-LY-DX001',
    projectName: '炼油厂 2026 年大修消缺防腐保温工程',
    publisherName: '陕西延长石油（集团）有限责任公司炼化公司',
    publishTime: d(1, 10, 0),
    status: 'PUBLISHED',
    publicLevel: 'SOCIAL',
    contentPreview: '本公告由招标人在本平台发布…项目位于陕西榆林，内容含招标范围、投标人资格、文件获取等。',
  },
  {
    id: 7002,
    noticeCode: 'BID-ENT-2026-0002',
    noticeType: 'WIN_RESULT_PUBLICITY',
    noticeTypeName: INTERNAL_TYPE_LABEL.WIN_RESULT_PUBLICITY,
    title: '杨凌 LNG 储罐防腐保温保冷项目中标结果公示（本平台发布）',
    projectCode: 'DYZB2026-YL-LNG001',
    projectName: '杨凌 LNG 储罐防腐保温保冷项目',
    publisherName: '陕西某燃气集团杨凌分公司',
    publishTime: d(5, 15, 30),
    status: 'PUBLISHED',
    publicLevel: 'SOCIAL',
    contentPreview: '中标结果公示：确定 XX 建设集团有限公司为本项目中标人，公示期三日。',
  },
];
const internalDetails: Record<string, InternalNoticeDetail> = {
  7001: {
    ...internalList[0]!,
    contentVersion: 3,
    content: '一、招标条件\n 本招标项目已由主管部门批准建设…\n二、项目概况与招标范围\n 项目名称：榆林炼油厂 2026 年大修消缺项目防腐保温工程；工程内容：装置保温保冷拆除与恢复、管道防腐。\n三、投标人资格要求\n （一）须具备防腐保温工程专业承包资质；\n（二）本次招标不接受联合体投标。',
    payload: {
      LEGAL_NAME: '陕西延长石油（集团）有限责任公司炼化公司',
      PROJECT_CODE: 'DYZB2026-LY-DX001',
      NOTICE_SEND_TIME: '20260908100000',
      SUBMIT_TIMESTAMP: '20260930170000',
      BID_OPEN_TIME: '20261001100000',
    },
    pdfFileId: 700001,
    pdfSize: 2_100_000,
  },
  7002: {
    ...internalList[1]!,
    contentVersion: 1,
    content: '中标结果公示\n 招标人已按招标文件确定的评标标准与办法完成评审，现将中标结果公示如下：中标人：XX 建设集团有限公司；中标金额：人民币 8,650,000.00 元。',
    payload: {
      LEGAL_NAME: '陕西某燃气集团杨凌分公司',
      PROJECT_CODE: 'DYZB2026-YL-LNG001',
      WINNER_NAME: 'XX 建设集团有限公司',
      WIN_AMOUNT: '8650000.00',
    },
    pdfFileId: 700002,
    pdfSize: 860_000,
  },
};
export function mockInternalPage(params: Record<string, unknown>): PageResult<InternalNoticeItem> {
  let rows = [...internalList];
  const kw = String(params.keyword ?? '').trim().toLowerCase();
  if (kw) {
    rows = rows.filter((r) =>
      [r.title, r.publisherName, r.projectName, r.projectCode, r.noticeTypeName]
        .filter(Boolean)
        .join(' ')
        .toLowerCase()
        .includes(kw),
    );
  }
  const nt = String(params.noticeType ?? '');
  if (nt) rows = rows.filter((r) => r.noticeType === nt);
  // 以下三项与真实接口口径保持一致（internalFlag / projectCode / publishTimeStart 下界），
  // 演示模式（useMock）下筛选也必须真的生效——否则"筛了没反应"会被误判成前端 bug。
  if (params.internalFlag !== undefined && params.internalFlag !== null && params.internalFlag !== '') {
    const want = params.internalFlag === true || params.internalFlag === 'true';
    rows = rows.filter((r) => (r.internalFlag === true) === want);
  }
  const pc = String(params.projectCode ?? '').trim().toLowerCase();
  if (pc) rows = rows.filter((r) => String(r.projectCode ?? '').toLowerCase() === pc);
  const from = String(params.publishTimeStart ?? '').trim();
  if (from) {
    const t = Date.parse(from.replace(' ', 'T'));
    if (!Number.isNaN(t)) rows = rows.filter((r) => toMs(String(r.publishTime)) >= t);
  }
  rows.sort((a, b) => toMs(String(b.publishTime)) - toMs(String(a.publishTime)));
  const pageNo = Math.max(1, Number(params.pageNo ?? 1) || 1);
  const pageSize = Math.min(100, Math.max(1, Number(params.pageSize ?? 10) || 10));
  const start = (pageNo - 1) * pageSize;
  return { list: rows.slice(start, start + pageSize), total: rows.length };
}
export function mockInternalDetail(id: string): InternalNoticeDetail | undefined {
  const hit = internalDetails[id];
  if (!hit) return undefined;
  return { ...hit, payload: hit.payload ? { ...hit.payload } : undefined };
}
