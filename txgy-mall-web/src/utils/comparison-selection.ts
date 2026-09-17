/** Only SKU identifiers are persisted. Prices, names and eligibility always come from the server. */
export const COMPARISON_LIMIT = 4
export const comparisonStorageKey = (tenant: string) => `mall-pc-comparison-v1:${tenant}`

export function comparisonIds(value: unknown): string[] {
  if (!Array.isArray(value) || value.length > COMPARISON_LIMIT) throw new Error('对比清单最多包含 4 个规格')
  const ids = value.map(id => {
    if (typeof id === 'number' && !Number.isSafeInteger(id)) throw new Error('商品规格编号无效')
    if ((typeof id !== 'string' && typeof id !== 'number') || !/^[1-9]\d*$/.test(String(id))) throw new Error('商品规格编号无效')
    return String(id)
  })
  if (new Set(ids).size !== ids.length) throw new Error('对比清单存在重复规格')
  return ids
}

export function readAnonymousComparison(storage: Pick<Storage, 'getItem'>, tenant: string): string[] {
  try { return comparisonIds(JSON.parse(storage.getItem(comparisonStorageKey(tenant)) || '[]')) } catch { return [] }
}

export function combineComparisonIds(saved: string[], anonymous: string[]): string[] {
  return comparisonIds([...new Set([...comparisonIds(saved), ...comparisonIds(anonymous)])])
}

export function comparisonQuantity(value: unknown): number {
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1 || value > 999) throw new Error('请输入 1—999 的整数采购份数')
  return value
}

export function difference(values: unknown[]): boolean {
  // Null and absent values are both unknown, never zero or an empty specification.
  return new Set(values.map(value => JSON.stringify(value ?? null))).size > 1
}
