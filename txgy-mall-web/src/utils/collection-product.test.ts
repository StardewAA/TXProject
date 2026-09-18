import { describe, expect, it } from 'vitest'
import { collectionEntry } from './collection-product'
describe('收藏/足迹失效商品呈现', () => {
  it('明确不可售时不生成价格卡片或详情链接，同时保留可删除的关系和商品编号', () => {
    const entry = collectionEntry({ id: 101, spuId: 300, spuName: '未审名称', price: null, saleable: false, unavailableReason: '等待商品审核' })
    expect(entry).toMatchObject({ relationId: 101, spuId: 300, unavailable: true, reason: '等待商品审核' })
    expect(entry.product).toBeUndefined()
  })
  it('旧API没有flag沿用可展示行为，并把关系id与spuId区分', () => {
    const entry = collectionEntry({ id: 101, spuId: 300, spuName: '保温材料', price: 1200 })
    expect(entry.product).toMatchObject({ id: 300, name: '保温材料', price: 1200 })
    expect(entry.relationId).toBe(101)
  })
  it('明确可售沿用商品展示', () => {
    expect(collectionEntry({ id: 3, spuId: 5, price: 100, saleable: true }).product?.price).toBe(100)
  })
  it('失效说明缺失时提供安全默认，不能用零元占位诱导购买', () => {
    const entry = collectionEntry({ id: 3, spuId: 5, price: 0, saleable: false, unavailableReason: '  ' })
    expect(entry.reason).toContain('不可购买'); expect(entry.product).toBeUndefined()
  })
  it('历史旧结构的商品id/name仍兼容且不修改源对象', () => {
    const raw = { id: 9, name: '旧商品', price: 500 }, before = { ...raw }
    expect(collectionEntry(raw).product).toMatchObject({ id: 9, name: '旧商品' }); expect(raw).toEqual(before)
  })
})
