/** Keep the two server dictionaries separate; a previous type's reason is not reusable implicitly. */
export function afterSaleReasons(config: Record<string, unknown> | null, way: number): string[] {
  if (!config || ![10, 20].includes(way)) return []
  const raw = way === 10 ? config.afterSaleRefundReasons : config.afterSaleReturnReasons
  const values = Array.isArray(raw) ? raw.filter((value): value is string => typeof value === 'string').map(value => value.trim()).filter(Boolean) : []
  return values.length ? [...new Set(values)] : ['其他']
}
