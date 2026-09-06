/**
 * 内置 Mock（后端 notice-external 未部署到联调环境时使用；VITE_BIDDING_USE_MOCK=1 开启）。
 *
 * 数据结构与 01-接口契约.md / 后端 VO 一致（列表行已带 sourceCode/legalFlag，正文含结构化块演示）；
 * 过滤语义与真实接口对齐：keyword 为包含检索（模拟 ES）、noticeTypeRaw/sourceCode/contentType/
 * industryFlag 均精确匹配、beginTime/endTime 取发布时间区间；真实联调后由 api.ts 直连后端。
 * 注意：Mock 演示数据非后端真实内容（页面有“演示 Mock 数据”标识）。
 */
import type {
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
    bidSectionCodes: ['BYB-001'],
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
    bidSectionCodes: ['TR-2026-01'],
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
  const map: Record<string, number> = { 近7天: 7, 近1个月: 30, 近3个月: 90, 今年以来: 365 };
  return iso(now - (map[quick] ?? 7) * DAY);
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
