// Payment deadlines are server facts. Do not invent a two-hour browser deadline.
export const monotonicNow = () =>
  typeof performance !== 'undefined' && typeof performance.now === 'function'
    ? performance.now()
    : Date.now();

export function epoch(value) {
  if (value === null || value === undefined || value === '' || typeof value === 'boolean') return null;
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : null;
}

export function stampPaymentClock(order, receivedAt = monotonicNow(), requestedAt = receivedAt) {
  const serverTime = epoch(order?.serverTime);
  return {
    ...order,
    // Add the round trip conservatively: never extend a payment window because a response was slow.
    paymentClock: serverTime === null ? null : {
      serverTime: serverTime + Math.max(0, receivedAt - requestedAt), receivedAt,
    },
  };
}

export function serverNow(order, now = monotonicNow()) {
  const clock = order?.paymentClock;
  return clock && epoch(clock.serverTime) !== null
    ? clock.serverTime + Math.max(0, now - clock.receivedAt)
    : null;
}

export function remainingText(milliseconds) {
  const seconds = Math.max(0, Math.ceil(milliseconds / 1000));
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map((value) => String(value).padStart(2, '0')).join(':');
}

export function orderPaymentState(order, now = monotonicNow()) {
  const result = { pending: Number(order?.status) === 0, canPay: false, expired: false, text: '', key: '' };
  if (!result.pending) return result;
  const reason = order?.paymentBlockReason;
  const waiting = reason === 'ERP_WAITING' || reason === 'ERP_WAIT_EXPIRED';
  const deadline = epoch(waiting ? order.erpWaitExpireTimestamp : order.payExpireTimestamp);
  const current = serverNow(order, now);
  const left = deadline !== null && current !== null ? deadline - current : null;
  result.key = `${order.id}:${waiting ? 'erp' : 'pay'}:${deadline}`;
  result.expired = reason === 'PAY_EXPIRED' || reason === 'ERP_WAIT_EXPIRED' || (left !== null && left <= 0);
  if (waiting) {
    result.text = result.expired
      ? '库存确认等待已到期，正在核实订单状态，请勿付款'
      : `待 ERP 确认库存，尚未开始支付计时${left !== null ? `；剩余等待 ${remainingText(left)}` : ''}`;
  } else if (result.expired) {
    result.text = '支付时限已到，正在核实支付与订单状态，请勿重复付款';
  } else if (reason === 'PAYMENT_UNCONFIRMED') {
    result.text = '支付结果核实中，请勿重复付款';
  } else if (reason === 'PAY_ORDER_MISSING') {
    result.text = '支付单尚未就绪，请稍后刷新';
  } else if (!reason && order.canPay === true && order.payOrderId && left !== null) {
    result.canPay = true;
    result.text = `剩余支付时间 ${remainingText(left)}，超时未支付将自动取消`;
  } else {
    result.text = '支付条件尚未确认，请刷新订单后重试';
  }
  return result;
}

// Goods cashiers must validate the trade-order relationship; recharges have no trade order.
export function cashierPaymentState(payOrder, tradeOrder, orderType, now = monotonicNow()) {
  const result = { pending: Number(payOrder?.status) === 0, canPay: false, expired: false, text: '', key: '' };
  if (!result.pending) return result;
  const deadline = epoch(payOrder.expireTimestamp) ?? epoch(payOrder.expireTime);
  const current = serverNow(payOrder, now);
  const left = deadline !== null && current !== null ? deadline - current : null;
  result.key = `cashier:${payOrder.id}:${deadline}`;
  result.expired = left !== null && left <= 0;
  if (result.expired) {
    result.text = '支付时限已到，正在核实支付结果，请勿重复付款';
    return result;
  }
  if (orderType !== 'recharge') {
    if (!tradeOrder || String(tradeOrder.payOrderId) !== String(payOrder.id)
      || String(tradeOrder.id) !== String(payOrder.merchantOrderId)) {
      result.text = '商城订单与支付单尚未核实，请刷新后重试';
      return result;
    }
    const tradeState = orderPaymentState(tradeOrder, now);
    if (!tradeState.canPay) return { ...tradeState, pending: result.pending };
  }
  result.canPay = left !== null && left > 0;
  result.text = result.canPay
    ? `剩余支付时间 ${remainingText(left)}`
    : '支付时限尚未确认，请刷新后重试';
  return result;
}
