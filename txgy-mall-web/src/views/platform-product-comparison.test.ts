import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const detail = readFileSync(new URL('./PlatformProductDetailView.vue', import.meta.url), 'utf8')

describe('public tenant product comparison entry', () => {
  it('uses the shared SKU comparison store and requires an explicit multi-SKU choice', () => {
    expect(detail).toContain("import { useComparisonStore } from '@/stores/comparison'")
    expect(detail).toContain('await comparison.add(selectedSku.value.skuId)')
    expect(detail).toContain('@click="selectSku(sku)"')
    expect(detail).toContain('请先点击选择需要对比的实际规格')
    expect(detail).toContain('>加入对比</el-button>')
    expect(detail).toContain('查看对比（{{ comparison.count }} / 4）')
  })
})
