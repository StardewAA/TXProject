import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const router = source('../router/index.ts')
const layout = source('../layouts/MallLayout.vue')
const purchaseLayout = source('../layouts/PurchaseLayout.vue')
const enterpriseProfile = source('./EnterpriseProfileView.vue')
const legacyEntry = source('./LegacyBusinessEntryView.vue')

describe('第21节商城用户端业务入口边界', () => {
  it('PC 保留企业入驻和采购中心入口', () => {
    expect(layout).toContain('to="/enterprise/profile">企业入驻')
    expect(layout).toContain('to="/purchase">采购中心')
    expect(purchaseLayout).toContain("'/purchase/inquiries/new'")
    expect(purchaseLayout).toContain("'/purchase/inquiry-quotes'")
    expect(purchaseLayout).toContain("'/purchase/orders'")
    expect(purchaseLayout).toContain("'/purchase/after-sales'")
  })

  it('PC 不再暴露销售中心导航或旧会员经营页面', () => {
    expect(layout).not.toContain('to="/sales"')
    expect(purchaseLayout).not.toContain('to="/sales"')
    expect(enterpriseProfile).not.toContain('to="/sales"')
    expect(router).not.toContain("component: () => import('@/views/Supplier")
    expect(router).toContain("path: 'sales/:pathMatch(.*)*'")
    expect(legacyEntry).toContain('供应经营入口已迁移至后台管理端')
  })

  it('当前商城可只读浏览租户内公开询价，正式报价仍进入供应商后台', () => {
    expect(layout).toContain('to="/inquiries"')
    expect(router).toContain("path: 'inquiries', name: 'inquiry-market', component: () => import('@/views/InquiryMarketView.vue')")
    expect(router).toContain("path: 'inquiries/:id(\\\\d+)', name: 'inquiry-public-detail', component: () => import('@/views/InquiryPublicDetailView.vue')")
    expect(router).not.toContain("import('@/views/InquiryQuoteView.vue')")
    expect(router).toContain("path: 'inquiries/:pathMatch(.*)*'")
    expect(legacyEntry).toContain('供应企业请在后台“销售管理—询价机会”')
  })
})
