import { describe, expect, it } from 'vitest';

import {
  mergeNoticeRows,
  type NoticeLinkItem,
  noticeTs,
  pageRows,
  rowKey,
  toSelfNoticeRow,
} from './noticeLink';

describe('noticeLink · 跨源合并纯函数', () => {
  it('noticeTs：外站 epoch 毫秒 / 数字串 / 本站 LocalDateTime 串 / 空值', () => {
    expect(noticeTs(1789000000000)).toBe(1789000000000);
    expect(noticeTs('1789000000000')).toBe(1789000000000);
    expect(noticeTs('2026-09-23 10:30:00')).toBe(new Date('2026-09-23T10:30:00').getTime());
    expect(noticeTs('')).toBe(0);
    expect(noticeTs(undefined)).toBe(0);
    expect(noticeTs(null)).toBe(0);
  });

  it('toSelfNoticeRow：字段对位 + 来源标本站', () => {
    const row = toSelfNoticeRow({
      id: '2101082428225581057',
      noticeCode: 'TG-2026-0001',
      noticeTypeName: '招标公告',
      publisherName: '西安鸥鹏互联科技有限公司',
      publishTime: '2026-09-23 10:30:00',
      status: 'PUBLISHED',
      title: '最严格闭环公告-59900',
    });
    expect(row).toMatchObject({
      id: '2101082428225581057',
      issuerName: '西安鸥鹏互联科技有限公司',
      noticeTypeName: '招标公告',
      publishTime: '2026-09-23 10:30:00',
      sourceCode: 'TG-2026-0001',
      sourceKind: 'SELF',
      sourceName: '本站',
      status: 'PUBLISHED',
      title: '最严格闭环公告-59900',
    });
  });

  it('mergeNoticeRows：按行键去重 + 跨源发布时间倒序（混合 epoch 毫秒 与 LocalDateTime 串）', () => {
    const ext: NoticeLinkItem[] = [
      { id: '1', sourceKind: 'EXTERNAL', publishTime: 1789000000000, title: '外站-旧' },
      { id: '1', sourceKind: 'EXTERNAL', publishTime: 1789000000000, title: '外站-重复' },
    ];
    const self: NoticeLinkItem[] = [
      { id: '9', sourceKind: 'SELF', publishTime: '2026-09-23 10:30:00', title: '本站-新' },
    ];
    const merged = mergeNoticeRows(ext, self);
    // 外站 epoch 1789000000000 ≈ 2026-09-08，本站 2026-09-23 更新 → 本站在前
    expect(merged.map((r) => r.title)).toEqual(['本站-新', '外站-旧']);
    expect(merged).toHaveLength(2); // 外站重复行被去重
  });

  it('mergeNoticeRows：id 空间不同不误去重（外站 id=1 与 本站 id=1 都保留）', () => {
    const merged = mergeNoticeRows(
      [{ id: '1', sourceKind: 'EXTERNAL', title: '外' }],
      [{ id: '1', sourceKind: 'SELF', title: '内' }],
    );
    expect(merged).toHaveLength(2);
    expect(new Set(merged.map(rowKey)).size).toBe(2);
  });

  it('pageRows：本地分页切片', () => {
    const all: NoticeLinkItem[] = Array.from({ length: 7 }, (_, i) => ({
      id: String(i),
      sourceKind: 'EXTERNAL' as const,
      title: 't' + i,
    }));
    expect(pageRows(all, 1, 5).map((r) => r.id)).toEqual(['0', '1', '2', '3', '4']);
    expect(pageRows(all, 2, 5).map((r) => r.id)).toEqual(['5', '6']);
    expect(pageRows(all, 3, 5)).toHaveLength(0);
  });
});
