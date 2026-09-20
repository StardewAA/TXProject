import type { MallSpuApi } from '#/api/mall/product/spu';

import { describe, expect, it } from 'vitest';

import { canAdminMaintain, canDecideReview, currentSubmission, parseReviewSnapshot, reviewStatusText, snapshotChanges } from './review-model';

const policy: MallSpuApi.ReviewPolicy = { adminWriteEnabled: false, legacyStrategy: 'KEEP_EXISTING_UNMODIFIED', reviewEnabled: true };
const product: MallSpuApi.Spu = { id: 8, reviewStatus: 10, contentVersion: 2, submissionId: 'submission-2' };
const submission: MallSpuApi.ReviewRecord = {
  id: 11, spuId: 8, contentVersion: 2, submissionId: 'submission-2',
  action: 'SUBMIT', actorType: 1, actorId: 3, snapshot: '{"name":"保温管","skus":[]}', createTime: '2026-09-15 12:00:00',
};

describe('后台商品审核安全模型', () => {
  it('后台试点按策略锁店，未知策略禁止维护，不硬编码其他店铺名单', () => {
    const trialPolicy = { ...policy, adminWriteEnabled: true, adminWriteLockedShopIds: [1] };
    expect(canAdminMaintain(undefined, 2)).toBe(false);
    expect(canAdminMaintain({ ...policy, adminWriteEnabled: true }, 2)).toBe(false);
    expect(canAdminMaintain(trialPolicy, 1)).toBe(false);
    expect(canAdminMaintain(trialPolicy, 2)).toBe(true);
    expect(canAdminMaintain({ ...trialPolicy, adminWriteEnabled: false }, 2)).toBe(false);
  });
  it('审核字段为空时显示历史过渡，绝不显示已通过', () => {
    expect(reviewStatusText(null)).toBe('历史过渡（未补审）');
    expect(reviewStatusText(undefined)).toBe('历史过渡（未补审）');
    expect(reviewStatusText(20)).toBe('审核通过');
  });
  it('策略失败、关闭或者当前非待审时禁止决策', () => {
    expect(canDecideReview(product, submission, policy)).toBe(true);
    expect(canDecideReview(product, submission, undefined)).toBe(false);
    expect(canDecideReview(product, submission, { ...policy, reviewEnabled: false })).toBe(false);
    for (const reviewStatus of [null, 0, 20, 30]) expect(canDecideReview({ ...product, reviewStatus }, submission, policy)).toBe(false);
  });
  it('版本、提交、商品或者动作不一致，不能使用旧页面审核', () => {
    for (const changed of [
      { contentVersion: 1 }, { submissionId: 'submission-1' }, { spuId: 9 }, { action: 'APPROVE' },
    ]) expect(canDecideReview(product, { ...submission, ...changed }, policy)).toBe(false);
  });
  it('只寻找匹配版本与提交的SUBMIT不可变记录', () => {
    const records = [{ ...submission, id: 12, action: 'APPROVE' }, { ...submission, id: 10, contentVersion: 1 }, submission];
    expect(currentSubmission(product, records)).toBe(submission);
    expect(currentSubmission({ ...product, submissionId: 'withdrawn' }, records)).toBeUndefined();
  });
  it('历史版本0补审和UUID提交编号不被错误转为数字', () => {
    const submissionId = '3eb71b18-7d73-4157-8e6d-2810f90e0b7f';
    const initial = { ...product, contentVersion: 0, submissionId };
    const record = { ...submission, contentVersion: 0, submissionId };
    expect(currentSubmission(initial, [record])).toBe(record);
    expect(canDecideReview(initial, record, policy)).toBe(true);
  });
  it('无快照或损坏快照不能退回当前商品内容', () => {
    for (const snapshot of ['', 'bad json', 'null', '[]', '"text"', '{}', '{"skus":{}}']) {
      expect(parseReviewSnapshot(snapshot)).toBeNull();
      expect(canDecideReview(product, { ...submission, snapshot }, policy)).toBe(false);
    }
  });
  it('SKU 库存流水和 ERP 映射不会污染经营差异', () => {
    const old = { name: '保温管', skus: [{ id: 1, price: 100, stock: 10, erpSkuId: 9 }] };
    const next = { name: '保温管', skus: [{ id: 1, price: 100, stock: 8, erpSkuId: 10 }] };
    expect(snapshotChanges(next, old)).toEqual([]);
  });
  it('价格、图文、配送、SKU资料均列入审核差异', () => {
    const old = { name: '保温管', picUrl: '/before.png', deliveryTemplateId: 1, skus: [{ id: 1, price: 100 }] };
    const next = { name: '保温管A', picUrl: '/after.png', deliveryTemplateId: 2, skus: [{ id: 1, price: 200 }] };
    expect(snapshotChanges(next, old).map((item) => item.key)).toEqual(['name', 'picUrl', 'skus', 'deliveryTemplateId']);
  });
  it('快照比较稳定排序对象字段，不因JSON字段顺序产生伪差异', () => {
    expect(snapshotChanges({ skus: [{ id: 1, price: 100 }] }, { skus: [{ price: 100, id: 1 }] })).toEqual([]);
  });
});
