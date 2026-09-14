import { describe, expect, it } from 'vitest'
import { buyerPath, buyerPayment, isPendingDispatch, isPurchasePath } from './buyer-navigation'

describe('采购中心导航边界', () => {
  it.each(['/purchase', '/purchase/orders/123', '/purchase/after-sales/apply'])('识别采购路由 %s', (path) => {
    expect(isPurchasePath(path)).toBe(true)
  })
  it.each(['/member', '/purchases', '/purchase-other'])('不误判其他路由 %s', (path) => {
    expect(isPurchasePath(path)).toBe(false)
  })
  it.each(['/member/orders', '/member/orders/123', '/member/orders/123/comment', '/member/after-sales', '/member/after-sales/apply', '/member/addresses'])('保留双中心导航 %s', (path) => {
    expect(buyerPath('/purchase/orders', path)).toBe(path.replace('/member', '/purchase'))
    expect(buyerPath('/member/orders', path)).toBe(path)
  })
  it('不修改商品地址或相似前缀', () => {
    expect(buyerPath('/purchase', '/goods/1')).toBe('/goods/1')
    expect(buyerPath('/purchase', '/membership')).toBe('/membership')
  })
  it('支付返回当前中心，兼容字符串编号', () => {
    expect(buyerPayment('/purchase/orders', '9', '123')).toEqual({ path: '/pay/9', query: { orderId: '123', redirect: '/purchase/orders/123' } })
    expect(buyerPayment('/member/orders', 9, 123).query.redirect).toBe('/member/orders/123')
  })
  it('待发货必须匹配订单路径及状态', () => {
    expect(isPendingDispatch('/sales/shops/1/orders', '10')).toBe(true)
    expect(isPendingDispatch('/sales/shops/1/orders', undefined)).toBe(false)
    expect(isPendingDispatch('/sales/shops/1/after-sales', '10')).toBe(false)
  })
})
