import { describe, expect, it, vi } from 'vitest'
import { cashierPaymentState, createPaymentClock, formatPaymentRemaining, isWalletCashierEntry, paymentTimestamp, tradePaymentState } from './payment-deadline'

const now = 1790000000000
const trade = { status: 0, payOrderId: 91, canPay: true, payExpireTimestamp: now + 60000 }
const pay = { id: 91, status: 0, expireTimestamp: now + 60000 }

describe('统一支付期限和可支付性', () => {
  it('使用明确 epoch；兼容旧服务中国时间，不取浏览器时区', () => {
    expect(paymentTimestamp(now)).toBe(now)
    expect(paymentTimestamp(String(now))).toBe(now)
    expect(paymentTimestamp('2026-09-15 20:00:00')).toBe(Date.parse('2026-09-15T12:00:00Z'))
    expect(paymentTimestamp('2026-09-15T12:00:00Z')).toBe(Date.parse('2026-09-15T12:00:00Z'))
    for (const bad of [undefined, null, '', 0, NaN, Infinity, 'tomorrow']) expect(paymentTimestamp(bad)).toBeUndefined()
  })
  it('订单列表/详情使用截止时间而不是打开页面时间', () => {
    expect(tradePaymentState(trade, now)).toMatchObject({ canPay: true, remaining: 60000 })
    expect(tradePaymentState(trade, now + 30000)).toMatchObject({ canPay: true, remaining: 30000 })
  })
  it('到达截止瞬间禁付，但不把订单状态改成已取消', () => {
    const state = tradePaymentState(trade, now + 60000)
    expect(state).toMatchObject({ canPay: false, expired: true, remaining: 0 })
    expect(state.message).toContain('核实')
    expect(trade.status).toBe(0)
  })
  it.each([10, 20, 30, 40])('主状态 %s 不继续显示付款倒计时', (status) => {
    expect(tradePaymentState({ ...trade, status }, now + 90000).kind).toBe('none')
  })
  it('支付事实优先于迟到的交易状态，禁止假取消', () => {
    expect(tradePaymentState({ ...trade, payStatus: true }, now + 90000).kind).toBe('none')
  })
  it('缺时间、缺支付单、后端拒付均安全禁付', () => {
    expect(tradePaymentState(trade).canPay).toBe(false)
    expect(tradePaymentState({ ...trade, canPay: false }, now).canPay).toBe(false)
    expect(tradePaymentState({ ...trade, canPay: undefined }, now).canPay).toBe(false)
    expect(tradePaymentState({ ...trade, payOrderId: undefined }, now).canPay).toBe(false)
    expect(tradePaymentState({ ...trade, payExpireTimestamp: undefined }, now).canPay).toBe(false)
  })
  it('ERP 单独计时，不冒充付款剩余时间', () => {
    expect(tradePaymentState({ ...trade, canPay: false, paymentBlockReason: 'ERP_WAITING', erpWaitExpireTimestamp: now + 120000 }, now))
      .toMatchObject({ kind: 'erp', canPay: false, remaining: 120000 })
  })
  it('ERP 等待超时仍只等待后端核实，不新生成支付资格', () => {
    expect(tradePaymentState({ ...trade, paymentBlockReason: 'ERP_WAIT_EXPIRED' }, now))
      .toMatchObject({ kind: 'erp', canPay: false, expired: true, remaining: 0 })
  })
  it('后端已判定超时即禁付，不被本地倒计时覆盖', () => {
    expect(tradePaymentState({ ...trade, paymentBlockReason: 'PAY_EXPIRED' }, now)).toMatchObject({ canPay: false, expired: true })
  })
  it('支付未知时不展示继续扫码或取消成功', () => {
    const state = tradePaymentState({ ...trade, paymentBlockReason: 'PAYMENT_UNCONFIRMED' }, now)
    expect(state.canPay).toBe(false)
    expect(state.message).toContain('正在核实')
  })
  it('收银台必须匹配路由关联交易订单的支付 ID', () => {
    expect(cashierPaymentState(pay, now, { ...trade, payOrderId: 92 }, true).canPay).toBe(false)
    expect(cashierPaymentState(pay, now, null, true).canPay).toBe(false)
    expect(cashierPaymentState(pay, now, trade, true).canPay).toBe(true)
  })
  it('收银台不能用仍未过期的支付单绕过交易状态禁付', () => {
    expect(cashierPaymentState(pay, now, { ...trade, status: 40 }, true).canPay).toBe(false)
    expect(cashierPaymentState(pay, now, { ...trade, canPay: false }, true).canPay).toBe(false)
  })
  it('钱包充值可独立使用支付期限，不强制查询交易订单', () => {
    expect(cashierPaymentState(pay, now)).toMatchObject({ canPay: true, remaining: 60000 })
    expect(cashierPaymentState(pay, now + 60000)).toMatchObject({ canPay: false, expired: true })
    expect(cashierPaymentState(pay).canPay).toBe(false)
  })
  it('直接收银台 URL 缺关联不能默认免核实，只有明确钱包入口作充值上下文', () => {
    expect(isWalletCashierEntry({})).toBe(false)
    expect(isWalletCashierEntry({ redirect: '/member/orders' })).toBe(false)
    expect(isWalletCashierEntry({ orderId: '7', redirect: '/member/wallet' })).toBe(false)
    expect(isWalletCashierEntry({ orderId: '', redirect: '/member/wallet' })).toBe(false)
    expect(isWalletCashierEntry({ redirect: ['/member/wallet'] })).toBe(false)
    expect(isWalletCashierEntry({ redirect: '/member/wallet' })).toBe(true)
    expect(cashierPaymentState(pay, now, null, !isWalletCashierEntry({})).canPay).toBe(false)
  })
  it('真实已支付状态优先于过期时间及交易同步延迟', () => {
    expect(cashierPaymentState({ ...pay, status: 10 }, now + 60000, trade, true).kind).toBe('none')
  })
  it('灰度期间两个服务期限不同时，界面不能延长任何一个期限', () => {
    expect(cashierPaymentState({ ...pay, expireTimestamp: now + 120000 }, now, trade, true).deadline).toBe(trade.payExpireTimestamp)
    expect(cashierPaymentState({ ...pay, expireTimestamp: now + 30000 }, now, trade, true).deadline).toBe(now + 30000)
  })
  it('优先读取 epoch；旧日期仅作兼容', () => {
    expect(cashierPaymentState({ ...pay, expireTime: '2099-01-01 00:00:00' }, now).deadline).toBe(pay.expireTimestamp)
  })
  it('剩余时间正确取整并支持超过一天的期限', () => {
    expect(formatPaymentRemaining(1)).toBe('00:00:01')
    expect(formatPaymentRemaining(-1)).toBe('00:00:00')
    expect(formatPaymentRemaining(90061000)).toBe('25:01:01')
  })
})

describe('服务端校时', () => {
  it('修改电脑时间不会重置已校准的支付剩余时长', () => {
    let elapsed = 100
    const clock = createPaymentClock(() => elapsed)
    clock.sync(now)
    const date = vi.spyOn(Date, 'now').mockReturnValue(now - 99999999)
    elapsed += 20000
    expect(clock.now()).toBe(now + 20000)
    date.mockRestore()
  })
  it('恢复/失效后必须重新取得服务端时间，缺失时不猜测', () => {
    const clock = createPaymentClock(() => 0)
    expect(clock.now()).toBeUndefined()
    clock.sync(now)
    clock.invalidate()
    expect(clock.now()).toBeUndefined()
    clock.sync(undefined)
    expect(clock.now()).toBeUndefined()
    clock.sync(now + 60000)
    expect(clock.now()).toBe(now + 60000)
  })
})
