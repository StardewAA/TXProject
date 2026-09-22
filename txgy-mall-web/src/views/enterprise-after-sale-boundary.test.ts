import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const source = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8')
const list = source('./AfterSalesView.vue')
const apply = source('./AfterSaleApplyView.vue')
const orders = source('./OrdersView.vue')
const detail = source('./OrderDetailView.vue')
const layout = source('../layouts/PurchaseLayout.vue')
const cart = source('./CartView.vue')

describe('enterprise purchase after-sale boundary', () => {
  it('uses enterprise after-sale endpoints in purchase routes', () => {
    expect(list).toContain("route.path.startsWith('/purchase/after-sales')")
    expect(list).toContain('tradeApi.getPurchaseAfterSales')
    expect(list).toContain('tradeApi.getPurchaseAfterSale')
    expect(list).toContain('tradeApi.deliverPurchaseAfterSale')
    expect(list).toContain('tradeApi.cancelPurchaseAfterSale')
    expect(apply).toContain('tradeApi.getPurchaseOrder')
    expect(apply).toContain('tradeApi.createPurchaseAfterSale')
  })

  it('lets enterprise colleagues read progress but keeps financial mutations with the purchaser', () => {
    expect(list).toContain('退款仍退回原支付账户')
    expect(list).toContain('item.createdByCurrentMember !== false && item.status === 20')
    expect(list).toContain('item.createdByCurrentMember !== false && [10, 20].includes(item.status)')
    expect(apply).toContain('仅实际下单人可申请售后')
    expect(apply).toContain('不能代替付款会员发起退款')
    expect(orders).toContain('可协作查看售后；支付、评价和售后变更由实际下单人执行')
    expect(detail).toContain('售后变更由实际下单人操作')
    expect(layout).toContain('售后申请与退货操作由实际下单人处理')
  })

  it('groups mixed-shop carts for the server-authoritative platform checkout', () => {
    expect(cart).toContain('cartStore.tenantGroups')
    expect(cart).toContain('v-for="shop in tenant.shops"')
    expect(cart).toContain('platformCheckoutDraft.save(cartStore.selectedItems.map(item => item.id))')
    expect(cart).not.toContain('mixedShopSelection.value')
  })
})
