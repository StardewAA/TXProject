/** Epoch fields are authoritative. Legacy local dates use the server's China time zone. */
export function paymentTimestamp(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value) && value > 0) return value
  if (typeof value !== 'string' || !value.trim()) return undefined
  if (/^\d{13}$/.test(value)) return Number(value)
  const text = value.trim().replace(' ', 'T')
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?(Z|[+-]\d{2}:?\d{2})?$/.test(text)) return undefined
  const timestamp = Date.parse(/(Z|[+-]\d{2}:?\d{2})$/.test(text) ? text : `${text}+08:00`)
  return Number.isFinite(timestamp) ? timestamp : undefined
}

export interface PaymentDeadlineFields {
  status?: number
  payStatus?: boolean
  payOrderId?: string | number
  payExpireTime?: string | number
  erpWaitExpireTime?: string | number
  payExpireTimestamp?: number
  erpWaitExpireTimestamp?: number
  serverTime?: number
  canPay?: boolean
  paymentBlockReason?: string
}

/** Explicit legacy recharge entry only; redirect is UI context, never backend authorization. */
export function isWalletCashierEntry(query: { orderId?: unknown; redirect?: unknown }): boolean {
  return query.orderId === undefined && query.redirect === '/member/wallet'
}

export interface PaymentDeadlineState {
  kind: 'none' | 'payment' | 'erp' | 'blocked'
  canPay: boolean
  expired: boolean
  message: string
  deadline?: number
  remaining?: number
}

const inactive = (): PaymentDeadlineState => ({ kind: 'none', canPay: false, expired: false, message: '' })
const blocked = (message: string): PaymentDeadlineState => ({ kind: 'blocked', canPay: false, expired: false, message })

function timedState(kind: 'payment' | 'erp', deadline: number | undefined, now: number | undefined): PaymentDeadlineState {
  const remaining = deadline !== undefined && now !== undefined ? Math.max(0, deadline - now) : undefined
  const expired = remaining === 0
  return {
    kind, deadline, remaining, expired,
    canPay: kind === 'payment' && remaining !== undefined && remaining > 0,
    message: kind === 'erp'
      ? expired ? 'ERP 库存确认等待已到期，正在等待服务端处理；暂不可付款。' : '等待 ERP 确认库存，当前暂不可付款；支付期限以服务端确认结果为准。'
      : expired ? '支付已到期，正在核实或关闭订单；请查询最新状态，勿重复付款。'
        : remaining === undefined ? '支付期限或服务端时间暂未核实，请刷新后再支付。' : '请在支付期限内完成付款，超时未付款将由服务端自动取消。',
  }
}

export function tradePaymentState(order: PaymentDeadlineFields | null | undefined, now?: number): PaymentDeadlineState {
  if (!order || Number(order.status) !== 0 || order.payStatus === true) return inactive()
  const reason = order.paymentBlockReason
  if (reason === 'PAYMENT_UNCONFIRMED') return blocked('支付结果正在核实，请刷新订单或查询支付结果，勿重复付款。')
  if (reason === 'ERP_WAITING' || reason === 'ERP_WAIT_EXPIRED') {
    const state = timedState('erp', paymentTimestamp(order.erpWaitExpireTimestamp) ?? paymentTimestamp(order.erpWaitExpireTime), now)
    if (reason === 'ERP_WAIT_EXPIRED') {
      state.expired = true
      state.remaining = 0
      state.message = 'ERP 库存确认等待已到期，正在等待服务端处理；暂不可付款。'
    }
    return state
  }
  if (reason === 'ORDER_NOT_UNPAID') return blocked('订单状态已变化，请刷新查看最新进度。')
  if (reason === 'PAY_ORDER_MISSING' || !order.payOrderId) return blocked('尚未生成可用支付单，请刷新订单查看处理进度。')
  const state = timedState('payment', paymentTimestamp(order.payExpireTimestamp) ?? paymentTimestamp(order.payExpireTime), now)
  if (reason === 'PAY_EXPIRED') {
    state.expired = true
    state.remaining = 0
    state.message = '支付已到期，正在核实或关闭订单；请查询最新状态，勿重复付款。'
  }
  state.canPay = state.canPay && order.canPay === true && !state.expired && !reason
  if (!state.canPay && !state.expired && state.remaining !== undefined) state.message = '服务端暂未开放付款，请刷新订单核实处理进度。'
  return state
}

export function cashierPaymentState(
  payOrder: Record<string, unknown> | null,
  now?: number,
  tradeOrder?: PaymentDeadlineFields | null,
  requiresTradeOrder = false,
): PaymentDeadlineState {
  if (!payOrder || Number(payOrder.status) !== 0) return inactive()
  if (requiresTradeOrder) {
    if (!tradeOrder || !payOrder.id || String(tradeOrder.payOrderId) !== String(payOrder.id)) return blocked('交易订单与支付单关联暂未核实，请刷新或返回订单。')
    const tradeState = tradePaymentState(tradeOrder, now)
    if (!tradeState.canPay) return tradeState.kind === 'none' ? blocked('交易订单已不处于待支付状态，请查询最新结果。') : tradeState
  }
  const state = timedState('payment', paymentTimestamp(payOrder.expireTimestamp) ?? paymentTimestamp(payOrder.expireTime), now)
  if (requiresTradeOrder && tradeOrder) {
    const tradeDeadline = tradePaymentState(tradeOrder, now).deadline
    if (tradeDeadline !== undefined && (state.deadline === undefined || tradeDeadline < state.deadline)) {
      // Never extend either service's deadline when versions temporarily differ during deployment.
      return timedState('payment', tradeDeadline, now)
    }
  }
  return state
}

export function formatPaymentRemaining(milliseconds: number): string {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1000))
  const hours = Math.floor(seconds / 3600)
  return `${String(hours).padStart(2, '0')}:${String(Math.floor(seconds % 3600 / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
}

/** Browser wall-clock changes cannot extend a deadline after server calibration. */
export function createPaymentClock(monotonicNow: () => number = () => performance.now()) {
  let sample: { server: number; elapsed: number } | undefined
  return {
    sync(serverTime: unknown) {
      const server = paymentTimestamp(serverTime)
      sample = server === undefined ? undefined : { server, elapsed: monotonicNow() }
    },
    invalidate() { sample = undefined },
    now() { return sample ? sample.server + Math.max(0, monotonicNow() - sample.elapsed) : undefined },
  }
}
