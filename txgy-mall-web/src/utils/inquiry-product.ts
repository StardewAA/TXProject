import type { Product, ProductSku, Category } from '@/types/mall'
import type { InquiryItem } from '@/api/inquiry'
import { inquiryCategories } from './inquiry-category'
export function inquirySpecification(sku: ProductSku) {
  return sku.properties?.map(p => [p.propertyName, p.valueName].filter(Boolean).join(':')).filter(Boolean).join(' / ') || '默认规格'
}
export function inquiryProductItem(product: Product, skuId: string, unit: string, categories: Category[]): InquiryItem {
  const sku = product.skus?.find(value => String(value.id) === skuId)
  if (!sku) throw new Error('请选择当前商品的具体规格')
  if (!unit.trim() || unit.trim().length > 20) throw new Error('请填写1至20字的采购单位')
  const id = Number(sku.id)
  if (!Number.isSafeInteger(id) || id <= 0) throw new Error('商品规格编号无效，请重新选择')
  const pic = sku.picUrl || product.picUrl
  let picUrl: string | undefined
  if (pic && pic.length <= 512) {
    try { const url = new URL(pic); if (['http:', 'https:'].includes(url.protocol) && url.hostname && !url.username && !url.password) picUrl = pic }
    catch { /* 历史相对地址不带入，可另行上传参考图片。 */ }
  }
  return { skuId: id, code: '', name: product.name,
    category: inquiryCategories(categories).find(value => value.id === String(product.categoryId))?.name || '',
    specification: inquirySpecification(sku), quantity: 1, unit: unit.trim(), remark: '', picUrl }
}
