/**
 * 关联公告（投标流程跟踪 →「本站 + 外站」合并展示）纯函数集。
 * ---------------------------------------------------------------------------
 * 【为什么有本文件】views/bidding/processTrack/modules/NoticeLinkModal.vue 只做装配
 * （发请求 / 拼参数 / 表格），跨源合并、字段对位、排序、分页全是纯逻辑 —— 抽出来 vitest 直测，
 * 不依赖 antd / vue 挂载，避免「改一行逻辑要起整个弹窗」。
 *
 * 【跨源口径（实测）】流程行的 projectCode = 招标项目编号（ProcessTrackItemVO 注释），
 *   本站公告列表（/bidding/notice/page）的 projectCode 参数**命不中**它（实测 total 0），
 *   必须用 tenderProjectCode（精确匹配权威数据集 TENDER_PROJECT_CODE）才命中（实测 total 1）；
 *   标段编号 bidSectionCode 两源都直接命中。详见同目录 NoticeLinkModal.vue 文件头。
 * ---------------------------------------------------------------------------
 */
/** 统一行：外站（NoticeExternalRespVO 子集）与本站（NoticeRespVO 子集）都对到这一个形状 */
export interface NoticeLinkItem {
  id?: number | string;
  issuerName?: string;
  publishTime?: null | number | string;
  sourceCode?: string;
  sourceName?: string;
  title?: string;
  /** EXTERNAL=外站采集（详情 /portal/bidding/detail/:id）；SELF=本站（/portal/bidding/internal-detail/:id） */
  sourceKind: 'EXTERNAL' | 'SELF';
  noticeTypeName?: null | string;
  status?: null | string;
}
/** 本站列表行子集（NoticeRespVO：publishTime 是 LocalDateTime 串，非外站的 epoch 毫秒） */
export interface InternalNoticeRow {
  id?: number | string;
  noticeCode?: string;
  noticeTypeName?: null | string;
  title?: string;
  publisherName?: string;
  publishTime?: null | string;
  status?: null | string;
}

/** 行键：两源 id 空间不同（雪花各自独立），合并去重 / 分页统一用 `{sourceKind}:{id}` */
export function rowKey(r: NoticeLinkItem): string {
  return r.sourceKind + ':' + String(r.id ?? '');
}

/** 发布时间 → epoch 毫秒：外站=epoch 毫秒（number / 数字串），本站=LocalDateTime（'yyyy-MM-dd HH:mm:ss'） */
export function noticeTs(v?: null | number | string): number {
  if (v === null || v === undefined || v === '') return 0;
  if (typeof v === 'number') return v;
  const s = String(v).trim();
  if (/^\d+$/.test(s)) return Number(s);
  const t = Date.parse(s.replace(' ', 'T'));
  return Number.isNaN(t) ? 0 : t;
}

/** 本站行 → 统一行（字段对位：publisherName→issuerName；来源标「本站」） */
export function toSelfNoticeRow(r: InternalNoticeRow): NoticeLinkItem {
  return {
    id: r.id,
    issuerName: r.publisherName,
    noticeTypeName: r.noticeTypeName,
    publishTime: r.publishTime,
    sourceCode: r.noticeCode,
    sourceKind: 'SELF',
    sourceName: '本站',
    status: r.status,
    title: r.title,
  };
}

/** 跨源合并：按行键去重 → 发布时间倒序（同时间的保持先外站后本站，稳定） */
export function mergeNoticeRows(ext: NoticeLinkItem[], self: NoticeLinkItem[]): NoticeLinkItem[] {
  const seen = new Set<string>();
  const all: NoticeLinkItem[] = [];
  for (const r of [...ext, ...self]) {
    const k = rowKey(r);
    if (seen.has(k)) continue;
    seen.add(k);
    all.push(r);
  }
  return all.sort((a, b) => noticeTs(b.publishTime) - noticeTs(a.publishTime));
}

/** 本地分页切片（合并后的统一行按页码切） */
export function pageRows(all: NoticeLinkItem[], pageNo: number, pageSize: number): NoticeLinkItem[] {
  const from = (pageNo - 1) * pageSize;
  return all.slice(from, from + pageSize);
}
