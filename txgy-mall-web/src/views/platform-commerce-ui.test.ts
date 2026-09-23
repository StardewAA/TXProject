import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const cart = readFileSync(new URL('./CartView.vue', import.meta.url), 'utf8')
const checkout = readFileSync(new URL('./CheckoutView.vue', import.meta.url), 'utf8')
const detail = readFileSync(new URL('./PlatformProductDetailView.vue', import.meta.url), 'utf8')
const portal = readFileSync(new URL('./PlatformPortalView.vue', import.meta.url), 'utf8')
const history = readFileSync(new URL('./PlatformCheckoutsView.vue', import.meta.url), 'utf8')

describe('platform commerce ui boundaries', () => {
  it('renders the server grouping and only forwards server cart item ids', () => {
    expect(cart).toContain('cartStore.tenantGroups')
    expect(cart).toContain('platformCheckoutDraft.save(cartStore.selectedItems.map(item => item.id))')
    expect(cart).not.toContain('mixedShopSelection')
  })

  it('uses platform preview amounts and never falls back to the legacy order creator', () => {
    expect(checkout).toContain('platformCheckoutApi.preview')
    expect(checkout).toContain('platformPreview.tenantGroups')
    expect(checkout).toContain('platformCheckoutApi.submit')
    expect(checkout).toContain("result.payable && result.platformPayOrderId")
    expect(checkout).toContain('platformCheckout=${encodeURIComponent(result.checkoutNo)}')
    expect(checkout).not.toContain('tradeApi.createOrder')
  })

  it('opens a stable tenant product route and adds only its sku id', () => {
    expect(portal).toContain("name: 'tenant-product-detail'")
    expect(detail).toContain('portalApi.getTenantProduct(slug, id)')
    expect(detail).toContain('cartStore.add(selectedSku.value.skuId, quantity.value)')
    expect(detail).toContain("name: 'platform-login'")
    expect(detail).not.toMatch(/cartStore\.add\([^)]*tenantId/)
    expect(detail).toContain('/member/customer-service/shop/${product.shopId}')
    expect(detail).toContain('productSkuId: String(selectedSku.skuId)')
    expect(detail).toContain("resourceType: 'PRODUCT'")
    expect(detail).toContain('resourceId: String(product.spuId)')
  })

  it('makes formalized checkout orders discoverable and allows unpaid checkout to continue', () => {
    expect(history).toContain('platformCheckoutApi.page')
    expect(history).toContain('platformCheckoutApi.get')
    expect(history).toContain('按店铺生成正式订单')
    expect(history).toContain('detail.payable && detail.platformPayOrderId')
    expect(history).toContain('继续支付')
    expect(history).toContain("PARTIALLY_DELIVERED: '部分已发货'")
    expect(history).toContain("ALL_COMPLETED: '全部完成'")
    expect(history).toContain("PARTIALLY_REFUNDED: '部分退款'")
    expect(history).not.toContain('receiverMobile')
    expect(history).not.toContain('receiverDetailAddress')
  })
})
