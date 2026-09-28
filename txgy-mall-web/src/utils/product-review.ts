import type { ProductReviewCommand, SupplierProduct } from '@/api/supplier'

export const productReviewOptions = [
  { value: 0, label: '草稿' }, { value: 10, label: '待审核' },
  { value: 20, label: '审核通过' }, { value: 30, label: '审核驳回' },
]
export function productReviewLabel(status?: number | null): string {
  if (status == null) return '历史过渡 · 待补审'
  return productReviewOptions.find(option => option.value === status)?.label ?? '未知审核状态'
}
export function productReviewActions(product: Pick<SupplierProduct, 'reviewStatus' | 'status' | 'contentVersion' | 'approvedVersion' | 'submissionId'>) {
  const versionValid = Number.isSafeInteger(product.contentVersion) && Number(product.contentVersion) >= 0
  const known = product.reviewStatus == null || productReviewOptions.some(option => option.value === product.reviewStatus)
  const pending = product.reviewStatus === 10
  const approved = product.reviewStatus === 20 && versionValid && Number(product.contentVersion) > 0
    && product.approvedVersion === product.contentVersion
  return {
    edit: versionValid && known && !pending,
    submit: versionValid && (product.reviewStatus == null || product.reviewStatus === 0 || product.reviewStatus === 30),
    withdraw: versionValid && pending && typeof product.submissionId === 'string'
      && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(product.submissionId),
    publish: approved && product.status === 0,
    unpublish: versionValid && product.status === 1,
    pending,
  }
}
export function productReviewCommand(shopId: number, product: Pick<SupplierProduct, 'id' | 'contentVersion'>): ProductReviewCommand {
  if (!Number.isSafeInteger(shopId) || shopId <= 0 || !Number.isSafeInteger(product.id) || Number(product.id) <= 0
    || !Number.isSafeInteger(product.contentVersion) || Number(product.contentVersion) < 0) {
    throw new Error('商品版本或店铺信息不完整，请刷新后重试')
  }
  return { shopId, id: product.id!, expectedVersion: product.contentVersion! }
}
/** Only human-maintained content participates, never live stock or ERP state. */
export function productEditableFingerprint(product: SupplierProduct): string {
  return JSON.stringify({
    name: product.name, keyword: product.keyword, introduction: product.introduction,
    categoryId: product.categoryId, brandId: product.brandId, picUrl: product.picUrl,
    sliderPicUrls: product.sliderPicUrls, deliveryTemplateId: product.deliveryTemplateId,
    descriptionText: product.descriptionText?.trim() || '', specType: product.specType,
    skus: product.skus.map(sku => ({
      id: sku.id, price: sku.price, marketPrice: sku.marketPrice, picUrl: sku.picUrl,
      weight: sku.weight, volume: sku.volume, barCode: sku.barCode,
      initialStock: sku.id ? undefined : sku.initialStock, properties: sku.id ? undefined : sku.properties,
    })),
  })
}
export function reviewSnapshotText(snapshot?: string | null): string {
  if (!snapshot) return '该记录没有内容快照'
  try { return JSON.stringify(JSON.parse(snapshot), null, 2) } catch { return snapshot }
}
/** New loads and unmount invalidate old responses without mutating server state. */
export function createProductReviewRequestGate() {
  let generation = 0
  return { begin: () => ++generation, current: () => generation, isCurrent: (ticket: number) => ticket === generation, invalidate: () => { generation++ } }
}
