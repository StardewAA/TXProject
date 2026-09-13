import type { Category } from '@/types/mall'

export interface InquiryCategoryOption { id: string; name: string; path: string }
// 同时兼容平铺和嵌套分类；显示路径只辅助核对，保存仍沿用分类名称快照。
export function inquiryCategories(categories: Category[]): InquiryCategoryOption[] {
  const nodes = new Map<string, { name: string; parent?: string }>()
  function collect(rows: Category[], parent?: string, ancestors = new Set<string>()) {
    for (const row of rows) {
      const id = String(row.id)
      if (ancestors.has(id)) continue
      const old = nodes.get(id)
      nodes.set(id, { name: row.name, parent: row.parentId != null && String(row.parentId) !== '0'
        ? String(row.parentId) : parent || old?.parent })
      collect(row.children || [], id, new Set([...ancestors, id]))
    }
  }
  collect(categories)
  return [...nodes].filter(([, row]) => row.name.trim().length > 0 && row.name.length <= 100).map(([id, row]) => {
    const names = [row.name], seen = new Set([id]); let parent = row.parent
    while (parent && nodes.has(parent) && !seen.has(parent)) {
      seen.add(parent); const ancestor = nodes.get(parent)!
      names.unshift(ancestor.name); parent = ancestor.parent
    }
    return { id, name: row.name, path: names.join(' / ') }
  })
}

export function checkInquiryCategory(value: string, options: InquiryCategoryOption[], ready: boolean) {
  if (!ready) return '分类目录尚未加载，暂无法核对；可保留手填分类'
  const name = value.trim()
  if (!name) return '请选择或填写物品分类'
  const matches = options.filter(option => option.name.trim() === name)
  if (!matches.length) return '未匹配商城分类：请核对文字；非商城物料可保留手填分类'
  if (matches.length > 1) return `存在${matches.length}个同名分类：${matches.map(option => option.path).join('；')}。请在备注注明所属范围`
  return `名称匹配：${matches[0]!.path}（不代表已绑定分类编号）`
}
