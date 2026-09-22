import { describe, expect, it } from 'vitest';

import { canDeliver, commonFilters, countText, exactEventIds, nextBannerCount, ORDER_STATUS_TABS, pageFilters } from './workbench-model';

describe('后台交易工作台范围与状态', () => {
  it('主状态不混入售后、评价或ERP状态', () => {
    expect(ORDER_STATUS_TABS.map((tab) => tab.key)).toEqual(['all', '0', '10', '20', '30', '40']);
  });
  it('页签统计保留共同条件且移除分页及单个状态', () => {
    expect(commonFilters({ status: 30, pageNo: 9, pageSize: 20, no: 'A', shopId: 2, createTime: ['x', 'y'] }))
      .toEqual({ no: 'A', shopId: 2, createTime: ['x', 'y'] });
  });
  it('自提单选与后端数组契约一致', () => {
    expect(commonFilters({ pickUpStoreId: 3 })).toEqual({ pickUpStoreIds: [3] });
    expect(commonFilters({ pickUpStoreId: undefined })).toEqual({});
  });
  it('用户选中页签不会被旧表单状态覆盖', () => {
    expect(pageFilters({ status: 30, no: 'A' }, '10')).toEqual({ no: 'A', status: 10 });
    expect(pageFilters({ status: 30 }, 'all').status).toBeUndefined();
  });
  it('统计失败显示未知，不显示假零', () => {
    expect(countText(0)).toBe('0');
    for (const value of [undefined, null, Number.NaN, -1, '2']) expect(countText(value)).toBe('—');
    expect(countText(10, true)).toBe('—');
  });
  it('发货依赖服务端允许，ERP待确认/未知字段均不猜测', () => {
    expect(canDeliver({ status: 10, deliveryType: 1, deliverable: true })).toBe(true);
    expect(canDeliver({ status: 10, deliveryType: 1 })).toBe(false);
    expect(canDeliver({ status: 10, deliveryType: 1, deliverable: false })).toBe(false);
    expect(canDeliver({ status: 20, deliveryType: 1, deliverable: true })).toBe(false);
  });
  it('已读提交精确去重的有效事件ID，不使用订单ID或高水位', () => {
    expect(exactEventIds([{ eventId: '9007199254740993', unread: true }, { eventId: '9007199254740993', unread: true }, { eventId: '4', unread: false }, { eventId: '-1', unread: true }])).toEqual(['9007199254740993']);
  });
  const snapshot = { scopeKey: '1:2:1:ADMIN:0', newCount: 2, unreadCount: 4, pendingCount: 5, resetRequired: false };
  it('初次进入不弹历史，仍由汇总显示真实未读数量', () => {
    expect(nextBannerCount(undefined, snapshot, 0)).toBe(0);
  });
  it('切身份/租户及游标重置不带入上个范围提醒', () => {
    expect(nextBannerCount(snapshot, { ...snapshot, scopeKey: '2:2:1:ADMIN:0' }, 3)).toBe(0);
    expect(nextBannerCount(snapshot, { ...snapshot, resetRequired: true }, 3)).toBe(0);
  });
  it('重复轮询不叠加新增数量；新阶段事件重新提醒', () => {
    expect(nextBannerCount(snapshot, snapshot, 2)).toBe(2);
    expect(nextBannerCount(snapshot, { ...snapshot, newCount: 0 }, 2)).toBe(2);
    expect(nextBannerCount(snapshot, { ...snapshot, newCount: 1 }, 2)).toBe(1);
  });
  it('已读或已处理后不保留虚假的新增待办数', () => {
    expect(nextBannerCount(snapshot, { ...snapshot, pendingCount: 0 }, 2)).toBe(0);
    expect(nextBannerCount(snapshot, { ...snapshot, unreadCount: 0 }, 2)).toBe(0);
    expect(nextBannerCount(snapshot, { ...snapshot, newCount: 0, unreadCount: 1 }, 2)).toBe(1);
  });
});
