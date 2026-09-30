/**
 * 大厅「本站」来源合并口径测试（2026-09-23）。
 *
 * 为什么必须有：大厅此前只列外部采集公告，来源站字典里也没有「本站」——
 * 本平台自己发布的公告在门户大厅**根本看不到**。这件事有三处容易静默出错：
 *   ① 什么时候合并：带外部专属筛选时必须退回外部公告（否则筛选条件对本站无效却仍混入）；
 *   ② 怎么分页：必须**跨源真分页**（total = 两源真实 total 之和），不能像「中标公示」合并视图那样
 *      截到 200 条 —— 大厅是主浏览路径，1637 条缩成 205 条等于把分页深度做没了；
 *   ③ 怎么去重：去重键必须带来源（外部公告 id 与本站公告 id 来自两张表，撞号会吃掉真公告）。
 */
import { describe, expect, it } from 'vitest';

import {
  hallMergesSelf,
  type HallRow,
  mergeSelfRows,
  planSelfFetchSlices,
  planSelfWindow,
  SELF_SOURCE,
  selfKeywordHit,
  selfQueryOf,
  sliceMergedWindow,
} from './api';

function ext(id: string, time: number, title = '外部公告'): HallRow {
  return { id, publishTime: time, sourceKind: 'EXTERNAL', title };
}
function self(id: string, time: number, title = '本站公告'): HallRow {
  return { id, publishTime: time, sourceKind: 'SELF', title };
}

describe('hallMergesSelf · 什么时候合并本站', () => {
  it('默认视图（无任何筛选）合并', () => {
    expect(hallMergesSelf({ pageNo: 1, pageSize: 10 })).toBe(true);
    expect(hallMergesSelf({ pageNo: 1, pageSize: 10, quick: '' })).toBe(true);
  });

  it('关键词/编号查询**合并本站**（2026-10-02：编号查询查不到本站公告的验收缺陷）', () => {
    const base = { pageNo: 1, pageSize: 10 };
    // 原先关键词被当作"外部专属筛选"排除本站 → 大厅按编号/标题永远查不到本平台公告；
    // 后端 /notice/public/list?keyword= 实测对本站返回空（检索索引只覆盖外部域），
    // 故本站侧改为本地匹配（fetchSelfAllRows + selfKeywordHit）后合并。
    expect(hallMergesSelf({ ...base, keyword: '炼化' })).toBe(true);
    expect(hallMergesSelf({ ...base, keyword: 'B6100002026000005' })).toBe(true);
    // 编号精确搜索同样合并本站：public/list 的 bidSectionCode/projectCode/tenderProjectCode 是
    // 结构化精确参数，实测可命中本站公告（探针 2026-10-02），本站侧按维度真实检索而非排除。
    expect(
      hallMergesSelf({ ...base, codeSearch: { dims: ['bidSectionCode'], value: 'I6106002026000001001001' } }),
    ).toBe(true);
    expect(
      hallMergesSelf({ ...base, codeSearch: { dims: ['projectCode'], value: 'I6106002026000001001' } }),
    ).toBe(true);
  });

  it('带其余外部专属筛选都不合并（性质/形态/行业/时间/项目编号/来源站）', () => {
    const base = { pageNo: 1, pageSize: 10 };
    expect(hallMergesSelf({ ...base, noticeTypeRaw: '招标公告' })).toBe(false);
    expect(hallMergesSelf({ ...base, contentType: 'HTML' })).toBe(false);
    expect(hallMergesSelf({ ...base, industryFlag: 'BELONGS' })).toBe(false);
    expect(hallMergesSelf({ ...base, quick: '近 7 天' })).toBe(false);
    expect(hallMergesSelf({ ...base, beginTime: '2026-09-01 00:00:00' })).toBe(false);
    // 注意：直接传 projectCode 仍是外部专属（不合并本站）——「编号查询」合并本站的路径是
    // codeSearch（大厅编号查询专用入参，见 fetchHallPage ④ 分支）；两者语义不同。
    expect(hallMergesSelf({ ...base, projectCode: 'I610100202600000h' })).toBe(false);
    expect(hallMergesSelf({ ...base, sourceCode: SELF_SOURCE })).toBe(false);
    expect(hallMergesSelf({ ...base, sourceCode: 'QYZB' })).toBe(false);
  });
});

describe('selfKeywordHit · 本站关键词/编号本地匹配', () => {
  const row: HallRow = {
    id: '1',
    noticeCode: 'B6100002026000005',
    projectKey: 'I6106002026000001001',
    projectName: '炼化公司所属各单位2026年装置大修/消缺公开招标',
    sourceKind: 'SELF',
    title: '炼化公司所属各单位2026年装置大修/消缺公开招标招标公告',
  };
  it('编号精确（大小写不敏感）命中', () => {
    expect(selfKeywordHit(row, 'B6100002026000005')).toBe(true);
    expect(selfKeywordHit(row, 'b6100002026000005')).toBe(true);
    expect(selfKeywordHit(row, 'B6100002026000009')).toBe(false);
  });
  it('标题/项目编号/项目名命中', () => {
    expect(selfKeywordHit(row, '炼化')).toBe(true);
    expect(selfKeywordHit(row, 'I6106002026000001001')).toBe(true);
    expect(selfKeywordHit(row, '装置大修')).toBe(true);
    expect(selfKeywordHit(row, '不存在词')).toBe(false);
  });
  it('空关键词视为不过滤', () => {
    expect(selfKeywordHit(row, '')).toBe(true);
  });
});

describe('selfQueryOf · 大厅筛选 → 本站公开分页入参（能翻的照翻，翻不了的失败收敛）', () => {
  it('时间/项目编号/关键词照翻（本站有这些字段）', () => {
    expect(
      selfQueryOf({
        pageNo: 1,
        pageSize: 10,
        beginTime: '2026-09-01 00:00:00',
        endTime: '2026-09-30 23:59:59',
        keyword: '炼化',
        projectCode: 'I610100202600000h',
      }),
    ).toEqual({
      keyword: '炼化',
      projectCode: 'I610100202600000h',
      publishTimeStart: '2026-09-01 00:00:00',
      publishTimeEnd: '2026-09-30 23:59:59',
    });
  });

  it('外站公告性质原文透传成 noticeType（编码空间不同 → 查不到，而不是静默放宽成全部）', () => {
    expect(selfQueryOf({ pageNo: 1, pageSize: 10, noticeTypeRaw: '招标公告' }).noticeType).toBe('招标公告');
  });

  it('「中标公示」合并标记不透传（由调用方拆两次类型请求后合并）', () => {
    expect(selfQueryOf({ pageNo: 1, pageSize: 10, noticeTypeRaw: '__result_merge__' }).noticeType).toBeUndefined();
  });

  it('形态/行业没有对应字段 → 不下发（界面另给口径提示）', () => {
    expect(
      selfQueryOf({ pageNo: 1, pageSize: 10, contentType: 'HTML', industryFlag: 'BELONGS' }),
    ).toEqual({});
  });
});

describe('mergeSelfRows · 本站「中标公示」合并口径', () => {
  it('两类型去重后倒序切页，total 取两接口真实之和，截断时如实告知', () => {
    const rows = [self('1', 300), self('1', 300), self('2', 200), self('3', 100)];
    const page1 = mergeSelfRows(rows, 3, 1, 2);
    expect(page1.list.map((r) => String(r.id))).toEqual(['1', '2']);
    expect(page1.total).toBe(3);
    expect(page1.truncated).toBe(false);
    const many = Array.from({ length: 200 }, (_, i) => self(String(i), 1000 - i));
    const capped = mergeSelfRows(many, 260, 1, 10);
    expect(capped.total).toBe(260);
    expect(capped.truncated).toBe(true);
  });
});

describe('planSelfWindow · 跨源分页抓取窗口', () => {
  it('首页：本站 5 条时外部从第 0 条起抓，窗口 = 5+10=15（≤100 一次请求够）', () => {
    expect(planSelfWindow(1, 10, 5)).toEqual({ extOffset: 0, extLimit: 15 });
  });

  it('第 N 页：外部起点前移 selfTotal（排在前面的本站公告最多这么多条）', () => {
    // 第 3 页 offset=20，本站 5 条 → 外部从 15 起
    expect(planSelfWindow(3, 10, 5)).toEqual({ extOffset: 15, extLimit: 15 });
    // 首页且本站 0 条 → 退化为纯外部窗口
    expect(planSelfWindow(1, 10, 0)).toEqual({ extOffset: 0, extLimit: 10 });
  });

  it('本站很多时窗口仍被 100/页的接口上限夹住（靠多次抓取覆盖，不静默截断）', () => {
    expect(planSelfWindow(50, 10, 300).extLimit).toBe(100);
    expect(planSelfWindow(50, 10, 300).extOffset).toBe(490 - 300);
  });
});

describe('sliceMergedWindow · 合并窗口切片（不变量：窗口切片 == 全局合并集切片）', () => {
  /** 外部全集：时间 1000..981（下标 0..19）——注意与本站某条时间相同，专门制造并列 */
  const externalAll = Array.from({ length: 20 }, (_, i) => ext(String(1000 - i), 1000 - i));
  const selfRows = [self('s1', 1005), self('s2', 995)];
  /** 参考实现：全量合并（本站全量 + 外部全集）按发布时间倒序 —— 分页必须与它逐页一致 */
  const reference = [...selfRows, ...externalAll].sort(
    (a, b) => Number(b.publishTime ?? 0) - Number(a.publishTime ?? 0),
  );
  const refPage = (pageNo: number, pageSize: number) =>
    reference.slice((pageNo - 1) * pageSize, pageNo * pageSize);

  it('首页：本站最新一条排第一（本站公告确实进了大厅目录）', () => {
    const page1 = sliceMergedWindow(selfRows, externalAll.slice(0, 20), 0, 1, 3);
    expect(page1.map((r) => String(r.id))).toEqual(refPage(1, 3).map((r) => String(r.id)));
    expect(page1[0]!.sourceKind).toBe('SELF');
  });

  it('抓取切片覆盖窗口尾部：取整前移后必须多取一页（只取一页会缺行）', () => {
    // pageSize=3、本站 2 条、第 3 页：extOffset=4 → 对齐到 0，窗口 [4,9) 需要第 2 页
    const slices = planSelfFetchSlices(3, 3, selfRows.length);
    expect(slices.map((s) => `p${s.pageNo}@${s.startIndex}+${s.limit}`)).toEqual([
      'p1@0+5',
      'p2@5+5',
    ]);
    // 只取第一片（错误做法）会丢窗口尾部 —— 这正是该函数存在的理由
    const onlyFirst = sliceMergedWindow(selfRows, externalAll.slice(0, 5), 0, 3, 3);
    expect(onlyFirst).toHaveLength(1);
    const all = externalAll.slice(0, 10);
    expect(sliceMergedWindow(selfRows, all, 0, 3, 3)).toHaveLength(3);
  });

  it('逐页比对（pageSize=3，共 8 页）：无重叠、无跳号、无丢行', () => {
    const seen: string[] = [];
    for (let pageNo = 1; pageNo <= 8; pageNo += 1) {
      // 用生产同款切片计划取数（不自己造窗口），再断言与全局合并集逐页一致
      const slices = planSelfFetchSlices(pageNo, 3, selfRows.length);
      const extWindow: HallRow[] = [];
      for (const s of slices) extWindow.push(...externalAll.slice(s.startIndex, s.startIndex + s.limit));
      const extStartIndex = slices.length ? slices[0]!.startIndex : 0;
      const page = sliceMergedWindow(selfRows, extWindow, extStartIndex, pageNo, 3);
      expect(page.map((r) => `${r.sourceKind}:${r.id}`)).toEqual(
        refPage(pageNo, 3).map((r) => `${r.sourceKind}:${r.id}`),
      );
      seen.push(...page.map((r) => `${r.sourceKind}:${r.id}`));
    }
    expect(new Set(seen).size).toBe(seen.length); // 跨页无重复
  });

  it('外部公告 id 与本站公告 id 撞号时两条都在（去重按来源+id，不比裸 id）', () => {
    const page = sliceMergedWindow([self('1', 500)], [ext('1', 400)], 0, 1, 10);
    expect(page.map((r) => `${r.sourceKind}:${r.id}`)).toEqual(['SELF:1', 'EXTERNAL:1']);
  });
});
