import { describe, expect, it } from 'vitest'
import { inquiryCategories, checkInquiryCategory } from './inquiry-category'

describe('询价分类核对', () => {
  const options = inquiryCategories([{ id: 1, name: '材料', children: [{ id: 2, name: '板材' }] },
    { id: 3, parentId: 1, name: '管材' }, { id: 4, name: '其他', children: [{ id: 5, name: '板材' }] }])
  it('兼容嵌套和平铺并展示完整路径', () => {
    expect(options.find(row => row.id === '2')?.path).toBe('材料 / 板材')
    expect(options.find(row => row.id === '3')?.path).toBe('材料 / 管材')
  })
  it('同名分类提示歧义，不自动绑定', () => {
    expect(checkInquiryCategory('板材', options, true)).toContain('存在2个同名分类')
  })
  it('仅名称精确匹配并去首尾空格，不做近似映射', () => {
    expect(checkInquiryCategory(' 管材 ', options, true)).toContain('名称匹配')
    expect(checkInquiryCategory('管', options, true)).toContain('未匹配')
  })
  it('空值、空目录和加载失败不伪装成匹配成功', () => {
    expect(checkInquiryCategory('', options, true)).toContain('请选择')
    expect(checkInquiryCategory('管材', [], true)).toContain('未匹配')
    expect(checkInquiryCategory('管材', options, false)).toContain('暂无法核对')
  })
  it('重复ID不重复，循环父级和缺失父级不会无限循环', () => {
    const data = inquiryCategories([{ id: 1, parentId: 2, name: 'A' }, { id: 2, parentId: 1, name: 'B' },
      { id: 1, parentId: 2, name: 'A' }, { id: 3, parentId: 99, name: 'C' }])
    expect(data).toHaveLength(3)
    expect(data[0]?.path).toBe('B / A')
    expect(data[2]?.path).toBe('C')
  })
  it('超长及空分类不作为可保存候选', () => {
    expect(inquiryCategories([{ id: 1, name: '' }, { id: 2, name: 'a'.repeat(101) }])).toEqual([])
  })
})
