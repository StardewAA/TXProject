import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import {
  cashierPaymentState, epoch, orderPaymentState, remainingText, serverNow, stampPaymentClock,
} from '../sheep/helper/payment-deadline.mjs';

const SERVER = 1_800_000_000_000;
const trade = (overrides = {}) => stampPaymentClock({
  id: 12, status: 0, payOrderId: 34, serverTime: SERVER,
  canPay: true, payExpireTimestamp: SERVER + 60_000, ...overrides,
}, 100);
const pay = (overrides = {}) => stampPaymentClock({
  id: 34, status: 0, merchantOrderId: '12', serverTime: SERVER,
  expireTimestamp: SERVER + 60_000, ...overrides,
}, 100);

test('invalid timestamps never create a default deadline', () => {
  for (const value of [undefined, null, '', false, true, {}, -1, 0, NaN, Infinity, 'bad']) {
    assert.equal(epoch(value), null);
  }
  assert.equal(orderPaymentState(trade({ payExpireTimestamp: null }), 100).canPay, false);
  assert.equal(orderPaymentState(trade({ serverTime: null }), 100).canPay, false);
});
test('server anchored clock ignores local wall clock and accounts for request latency', () => {
  const data = stampPaymentClock({ serverTime: SERVER }, 200, 100);
  assert.equal(serverNow(data, 250), SERVER + 150);
  assert.equal(serverNow(data, 150), SERVER + 100);
});
test('payment can be started only before its exact server deadline', () => {
  assert.equal(orderPaymentState(trade(), 60_099).canPay, true);
  const expired = orderPaymentState(trade(), 60_100);
  assert.equal(expired.canPay, false);
  assert.equal(expired.expired, true);
  assert.match(expired.text, /正在核实/);
  assert.doesNotMatch(expired.text, /已取消/);
});
test('ERP waiting does not use or start the payment countdown', () => {
  const data = trade({ canPay: false, payOrderId: null, payExpireTimestamp: null,
    paymentBlockReason: 'ERP_WAITING', erpWaitExpireTimestamp: SERVER + 120_000 });
  const waiting = orderPaymentState(data, 100);
  assert.equal(waiting.canPay, false);
  assert.match(waiting.text, /尚未开始支付计时.*00:02:00/);
  assert.equal(orderPaymentState(data, 120_100).expired, true);
  assert.doesNotMatch(orderPaymentState(data, 120_100).text, /已取消/);
});
test('explicit backend blocks override a future browser deadline', () => {
  for (const reason of ['ERP_WAITING', 'ERP_WAIT_EXPIRED', 'PAY_EXPIRED',
    'PAYMENT_UNCONFIRMED', 'ORDER_NOT_UNPAID', 'PAY_ORDER_MISSING', 'FUTURE_REASON']) {
    assert.equal(orderPaymentState(trade({ paymentBlockReason: reason }), 100).canPay, false, reason);
  }
  assert.equal(orderPaymentState(trade({ canPay: false }), 100).canPay, false);
  assert.equal(orderPaymentState(trade({ canPay: undefined }), 100).canPay, false);
});
test('paid/cancelled/refunded trade orders never offer payment', () => {
  for (const status of [10, 20, 30, 40]) {
    const result = orderPaymentState(trade({ status }), 100);
    assert.equal(result.canPay, false);
    assert.equal(result.pending, false);
  }
});
test('cashier requires the same associated goods order and pay order', () => {
  assert.equal(cashierPaymentState(pay(), trade(), 'goods', 100).canPay, true);
  for (const data of [null, trade({ id: 99 }), trade({ payOrderId: 99 }), trade({ canPay: false })]) {
    assert.equal(cashierPaymentState(pay(), data, 'goods', 100).canPay, false);
  }
});
test('recharge cashier does not require a trade order', () => {
  assert.equal(cashierPaymentState(pay({ merchantOrderId: 'RECHARGE-23' }), null, 'recharge', 100).canPay, true);
  assert.equal(cashierPaymentState(pay(), null, 'recharge', 60_100).canPay, false);
});
test('cashier honors both pay-order and trade-order expiry', () => {
  assert.equal(cashierPaymentState(pay(), trade({ payExpireTimestamp: SERVER }), 'goods', 100).canPay, false);
  assert.equal(cashierPaymentState(pay({ expireTimestamp: SERVER }), trade(), 'goods', 100).canPay, false);
  for (const status of [10, 20, 30]) {
    assert.equal(cashierPaymentState(pay({ status }), trade(), 'goods', 100).canPay, false);
  }
});
test('formatting stays nonnegative and does not wrap after 24 hours', () => {
  assert.equal(remainingText(-1), '00:00:00');
  assert.equal(remainingText(1), '00:00:01');
  assert.equal(remainingText(25 * 3600_000), '25:00:00');
});

async function hookHarness(reload, getStates = () => []) {
  const callbacks = {};
  const timers = new Map();
  const listeners = new Map();
  let now = 100;
  let sequence = 0;
  const document = {
    hidden: false,
    addEventListener: (name, fn) => listeners.set(name, fn),
    removeEventListener: (name, fn) => { if (listeners.get(name) === fn) listeners.delete(name); },
  };
  const source = (await readFile(new URL('../sheep/hooks/usePaymentDeadline.js', import.meta.url), 'utf8'))
    .replace(/^import .*;\r?\n/gm, '').replace('export function usePaymentDeadline', 'function usePaymentDeadline');
  const factory = new Function('ref', 'onBeforeUnmount', 'onShow', 'onHide', 'onUnload',
    'monotonicNow', 'setInterval', 'clearInterval', 'document', `${source}\nreturn usePaymentDeadline;`);
  const useClock = factory((value) => ({ value }),
    (fn) => { callbacks.unmount = fn; }, (fn) => { callbacks.show = fn; },
    (fn) => { callbacks.hide = fn; }, (fn) => { callbacks.unload = fn; },
    () => now, (fn) => { timers.set(++sequence, fn); return sequence; },
    (id) => timers.delete(id), document);
  return { clock: useClock(getStates, reload), callbacks, timers, listeners, document,
    advance: (value) => { now += value; for (const tick of timers.values()) tick(); } };
}

test('onLoad/onShow refreshes coalesce, hiding pauses timers, and resuming reloads', async () => {
  let calls = 0;
  let resolve;
  const h = await hookHarness(() => { calls++; return new Promise((done) => { resolve = done; }); });
  const first = h.clock.refresh();
  h.callbacks.show();
  await Promise.resolve();
  assert.equal(calls, 1);
  assert.equal(h.timers.size, 1);
  resolve(true); await first;
  assert.equal(h.clock.confirmed.value, true);
  h.callbacks.hide();
  assert.equal(h.timers.size, 0);
  assert.equal(h.clock.active.value, false);
  h.callbacks.show();
  const resumed = h.clock.refresh();
  await Promise.resolve();
  assert.equal(calls, 2);
  assert.equal(h.clock.confirmed.value, false);
  resolve(true); await resumed;
  h.callbacks.unload();
  assert.equal(h.timers.size, 0);
  assert.equal(h.listeners.size, 0);
});
test('expiry refreshes once then retries at a bounded interval, never per tick', async () => {
  let calls = 0;
  const h = await hookHarness(() => { calls++; return true; },
    (now) => [{ pending: true, expired: now >= 1100, key: '12:pay:1000' }]);
  h.callbacks.show(); await h.clock.refresh();
  h.advance(1000); await h.clock.refresh();
  assert.equal(calls, 2);
  h.advance(1000); await Promise.resolve();
  assert.equal(calls, 2);
  h.advance(15_000); await h.clock.refresh();
  assert.equal(calls, 3);
  h.callbacks.unmount();
});
test('visibility restoration revalidates and a failed response keeps payment disabled', async () => {
  let succeed = true;
  const h = await hookHarness(() => succeed);
  h.callbacks.show(); await h.clock.refresh();
  h.document.hidden = true; h.listeners.get('visibilitychange')();
  assert.equal(h.clock.confirmed.value, false);
  succeed = false;
  h.document.hidden = false; h.listeners.get('visibilitychange')();
  await h.clock.refresh();
  assert.equal(h.clock.confirmed.value, false);
  h.callbacks.unload();
});

test('payment initiation exposes one promise without changing wallet/mock channel dispatch', async () => {
  const source = (await readFile(new URL('../sheep/platform/pay.js', import.meta.url), 'utf8'))
    .replace(/^import .*;\r?\n/gm, '')
    .replace('export default class SheepPay', 'class SheepPay')
    .replace(/export function /g, 'function ');
  const mockSheep = { $platform: { name: 'H5' }, $helper: { toast() {} } };
  const Pay = new Function('sheep', `${source}\nreturn SheepPay;`)(mockSheep);
  const pending = Promise.resolve('submitted');
  const calls = [];
  Pay.prototype.walletPay = () => { calls.push('wallet'); return pending; };
  Pay.prototype.mockPay = () => { calls.push('mock'); return pending; };
  const wallet = new Pay('wallet', 'goods', 34);
  assert.equal(wallet.pending, pending);
  assert.equal(await wallet.pending, 'submitted');
  assert.deepEqual(calls, ['wallet']);
  const mock = new Pay('mock', 'goods', 34);
  assert.equal(mock.pending, pending);
  assert.deepEqual(calls, ['wallet', 'mock']);
  await new Pay('missing-channel', 'goods', 34).pending;
  assert.deepEqual(calls, ['wallet', 'mock']);
});

test('a response started before hiding cannot re-enable payment after resuming', async () => {
  const resolvers = [];
  const h = await hookHarness(() => new Promise((resolve) => resolvers.push(resolve)));
  h.callbacks.show();
  const initial = h.clock.refresh();
  await Promise.resolve();
  h.callbacks.hide();
  h.callbacks.show();
  const resumed = h.clock.refresh();
  resolvers[0](true);
  await initial;
  await Promise.resolve();
  assert.equal(h.clock.confirmed.value, false);
  assert.equal(resolvers.length, 2);
  resolvers[1](true);
  await resumed;
  assert.equal(h.clock.confirmed.value, true);
  h.callbacks.unload();
});
