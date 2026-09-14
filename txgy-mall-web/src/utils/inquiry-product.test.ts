import { describe, it, expect } from 'vitest'
import { inquiryProductItem } from './inquiry-product'
import type { Product } from '@/types/mall'
describe('询价商品快照', () => {
  const product: Product = { id: 1, name: '保温板', price: 100, categoryId: 2, picUrl: 'https://example.com/main.png',
    skus: [{ id: 3, price: 100, stock: 5, picUrl: 'https://example.com/sku.png', properties: [{ propertyName: '厚度', valueName: '50mm' }] }] }
  it('带入规格、嵌套分类和独立图片', () => {
    expect(inquiryProductItem(product, '3', ' 块 ', [{ id: 1, name: '材料', children: [{ id: 2, name: '保温' }] }])).toMatchObject({ skuId: 3, name: '保温板', category: '保温', specification: '厚度:50mm', unit: '块', quantity: 1, picUrl: 'https://example.com/sku.png' })
  })
  it('拒绝跨商品规格和无效单位', () => {
    expect(() => inquiryProductItem(product, '999', '米', [])).toThrow('具体规格')
    expect(() => inquiryProductItem(product, '3', ' ', [])).toThrow('采购单位')
    expect(() => inquiryProductItem(product, '3', '米'.repeat(21), [])).toThrow('采购单位')
  })
  it('默认规格、缺失分类和主图回退', () => {
    expect(inquiryProductItem({ ...product, skus: [{ id: 3, price: 100, stock: 5 }] }, '3', '米', [])).toMatchObject({ specification: '默认规格', category: '', picUrl: product.picUrl })
  })
  it('不带入非法图片', () => {
    for (const picUrl of ['javascript:alert(1)', '/local.png', 'https://u:p@example.com/a'])
      expect(inquiryProductItem({ ...product, skus: [{ id: 3, price: 1, stock: 5, picUrl }] }, '3', '米', []).picUrl).toBeUndefined()
  })
})
