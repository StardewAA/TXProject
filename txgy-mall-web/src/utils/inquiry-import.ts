import type { ImportRow, InquiryItem } from '@/api/inquiry'

export type DuplicatePolicy = 'skip' | 'keep'
export const hasInquiryInput = (item: InquiryItem) => !!(item.name || item.code || item.category
  || item.specification || item.unit || item.remark || item.picUrl || item.skuId || item.quantity !== 1)

// 不猜测单位别名、分类映射或商品规格；只去除字段首尾空格。
function identity(item: InquiryItem): string {
  const text = (value?: string) => (value || '').trim()
  return JSON.stringify([item.skuId ? ['sku', item.skuId] : ['manual', text(item.code), text(item.name)],
    text(item.category), text(item.specification), text(item.unit)])
}

export function planInquiryImport(existing: InquiryItem[], preview: ImportRow[], policy: DuplicatePolicy) {
  const current = existing.filter(hasInquiryInput)
  const seen = new Map(current.map(item => [identity(item), { item, source: '现有明细' }]))
  const rows = preview.map(row => {
    const item: InquiryItem = { ...row.item, quantity: Number(row.item.quantity), skuId: row.skuId }
    const key = identity(item), previous = seen.get(key)
    const errors = [...row.errors]
    if (!Number.isFinite(item.quantity) || item.quantity <= 0) errors.push('数量不是有效正数')
    const duplicate = previous ? `与${previous.source}重复` : ''
    const different = !!previous && (previous.item.quantity !== item.quantity
      || (previous.item.remark || '').trim() !== (item.remark || '').trim())
    if (!previous && !errors.length) seen.set(key, { item, source: `Excel第${row.rowNumber}行` })
    return { ...row, item, errors, duplicate, different, skipped: policy === 'skip' && !!previous }
  })
  const additions = rows.filter(row => !row.skipped).map(row => row.item)
  return { rows, additions, existingCount: current.length, total: current.length + additions.length,
    duplicateCount: rows.filter(row => row.duplicate).length,
    skippedCount: rows.filter(row => row.skipped).length,
    errorCount: rows.filter(row => row.errors.length).length }
}
