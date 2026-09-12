import type { Category, Identifier } from '@/types/mall'

export function rootCategories(categories: Category[]) {
  return categories.filter(item => !item.parentId || String(item.parentId) === '0')
}

export function categoryIds(categories: Category[], id: Identifier): Set<string> {
  const ids = new Set([String(id)])
  let changed = true
  while (changed) {
    changed = false
    for (const item of categories) {
      if (ids.has(String(item.parentId)) && !ids.has(String(item.id))) {
        ids.add(String(item.id))
        changed = true
      }
    }
  }
  return ids
}
