import { describe, expect, it } from 'vitest'
import type { SupplierProduct } from '@/api/supplier'
import { createProductReviewRequestGate, productEditableFingerprint, productReviewActions, productReviewCommand, productReviewLabel, reviewSnapshotText } from './product-review'

const product = (overrides: Partial<SupplierProduct> = {}): SupplierProduct => ({
  id: 42, shopId: 7, name: '保温板', keyword: '保温', introduction: '工程材料', picUrl: 'https://example.com/board.png',
  contentVersion: 2, reviewStatus: 0, status: 0,
  skus: [{ id: 51, price: 1500, marketPrice: 1800, weight: 1, volume: 0.1, barCode: 'QA-51', stock: 100 }], ...overrides,
})
describe('商品审核动作矩阵', () => {
  it('历史商品不能冒充批准，允许补审和安全下架', () => {
    const row = product({ reviewStatus: null, contentVersion: 0, status: 1 })
    expect(productReviewLabel(row.reviewStatus)).toBe('历史过渡 · 待补审')
    expect(productReviewActions(row)).toMatchObject({ edit: true, submit: true, unpublish: true, publish: false })
    expect(productReviewActions({ ...row, status: 0 }).publish).toBe(false)
  })
  it.each([0, 30])('草稿/驳回 %s 可以编辑送审但不能上架', reviewStatus => {
    expect(productReviewActions(product({ reviewStatus }))).toMatchObject({ edit: true, submit: true, publish: false, withdraw: false })
  })
  it('待审冻结，只允许指定提交批次撤回', () => {
    expect(productReviewActions(product({ reviewStatus: 10, submissionId: '6063a409-9704-4555-88d6-0351f9ab88b8' }))).toMatchObject({ edit: false, submit: false, publish: false, withdraw: true })
    expect(productReviewActions(product({ reviewStatus: 10, submissionId: null })).withdraw).toBe(false)
  })
  it.each(['', '19', 'not-a-uuid', ' 6063a409-9704-4555-88d6-0351f9ab88b8 '])('缺失或非UUID批次%s不允许撤回', submissionId => {
    expect(productReviewActions(product({ reviewStatus: 10, submissionId })).withdraw).toBe(false)
  })
  it('只允许当前版本批准后单独上架，不允许旧批准版本上架新内容', () => {
    expect(productReviewActions(product({ reviewStatus: 20, approvedVersion: 2 })).publish).toBe(true)
    expect(productReviewActions(product({ reviewStatus: 20, approvedVersion: 1 })).publish).toBe(false)
    expect(productReviewActions(product({ reviewStatus: 20, approvedVersion: 2, status: 1 })).publish).toBe(false)
  })
  it('未知状态不放行编辑/送审/上架，仍允许安全下架', () => {
    expect(productReviewLabel(999)).toBe('未知审核状态')
    expect(productReviewActions(product({ reviewStatus: 999, status: 1 }))).toMatchObject({ edit: false, submit: false, publish: false, unpublish: true })
  })
  it.each([undefined, -1, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1])('版本不完整 %s 不放行写操作', contentVersion => {
    expect(productReviewActions(product({ contentVersion }))).toMatchObject({ edit: false, submit: false, publish: false, unpublish: false })
  })
})
describe('商品版本与表单保留', () => {
  it('每次变更携带页面读到的版本，不自动采用最新服务端版本', () => {
    expect(productReviewCommand(7, product())).toEqual({ shopId: 7, id: 42, expectedVersion: 2 })
    expect(productReviewCommand(7, product({ contentVersion: 0, reviewStatus: null })).expectedVersion).toBe(0)
  })
  it.each([{ id: undefined }, { id: 0 }, { contentVersion: undefined }, { contentVersion: -1 }])('残缺命令被拒绝 %j', row => {
    expect(() => productReviewCommand(7, product(row))).toThrow('商品版本')
  })
  it('动态库存和审核信息变化不制造经营修订', () => {
    const original = product(), copy = structuredClone(original)
    copy.skus[0]!.stock = 88; copy.skus[0]!.wmsManaged = true; copy.reviewStatus = 20; copy.contentVersion = 3
    expect(productEditableFingerprint(copy)).toBe(productEditableFingerprint(original))
  })
  it('名称、价格、图片、运费、替换详情都属于实际内容变化', () => {
    const original = product(), base = productEditableFingerprint(original)
    for (const changed of [product({ name: '保温板改' }), product({ picUrl: 'https://example.com/new.png' }), product({ deliveryTemplateId: 77 }), product({ descriptionText: '新详情' })]) {
      expect(productEditableFingerprint(changed)).not.toBe(base)
    }
    const copy = structuredClone(original); copy.skus[0]!.price += 1
    expect(productEditableFingerprint(copy)).not.toBe(base)
  })
  it('详情留空保留原文不制造差异，比较函数不改动表单', () => {
    const row = product({ description: '<p>原文</p>', descriptionText: '' }), before = structuredClone(row)
    expect(productEditableFingerprint(row)).toBe(productEditableFingerprint({ ...row, descriptionText: '  ' }))
    expect(row).toEqual(before)
  })
})
describe('请求和历史快照安全', () => {
  it('新请求和路由清理使旧请求响应失效', () => {
    const gate = createProductReviewRequestGate(), first = gate.begin(), second = gate.begin()
    expect(gate.isCurrent(first)).toBe(false); expect(gate.isCurrent(second)).toBe(true)
    gate.invalidate(); expect(gate.isCurrent(second)).toBe(false)
  })
  it('陈旧异步请求不会覆盖新商品', async () => {
    const gate = createProductReviewRequestGate(); let shown = ''
    let finish!: (value: string) => void
    const old = new Promise<string>(resolve => { finish = resolve }), first = gate.begin()
    const pending = old.then(value => { if (gate.isCurrent(first)) shown = value })
    const current = gate.begin(); if (gate.isCurrent(current)) shown = '新商品'
    finish('旧店铺商品'); await pending; expect(shown).toBe('新商品')
  })
  it('快照只生成文本，不执行其中的HTML', () => {
    expect(reviewSnapshotText('{"name":"<script>alert(1)</script>"}')).toContain('<script>')
    expect(reviewSnapshotText('历史不可解析内容')).toBe('历史不可解析内容')
    expect(reviewSnapshotText()).toBe('该记录没有内容快照')
  })
})
