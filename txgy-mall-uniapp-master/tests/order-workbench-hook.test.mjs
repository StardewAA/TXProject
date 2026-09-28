import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import * as helpers from '../sheep/helper/order-workbench.mjs';

// Run the production hook with explicit lifecycle/API dependencies, not a copied controller.
const source = readFileSync(new URL('../sheep/hooks/useOrderWorkbench.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace('export function useOrderWorkbench', 'function useOrderWorkbench');
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const settle = async () => { for (let n = 0; n < 12; n++) await Promise.resolve(); };
const response = (overrides = {}) => ({ code: 0, data: { scopeKey: '1:1:316:BUYER:0', cursor: '20', serverTime: 1789442400000,
  resetRequired: false, pendingCount: 2, unreadCount: 1, newCount: 0, entries: [], rowEntries: [],
  orderStatusCounts: Object.fromEntries(helpers.orderTabs.map(t => [t.count, 2])),
  todoCounts: { PAYMENT: 1, RECEIVE: 1, COMMENT: 0, BUYER_RETURN: 0 }, ...overrides } });
function harness() {
  const user = { isLogin: true, userInfo: { id: 316 } }, lifecycle = {}, requests = [], navigations = [], timers = new Map();
  let token = 'token-A', tenant = 1, timerId = 0;
  const api = { summary: async params => { requests.push(params); return response(); },
    page: async () => ({ code: 0, data: { list: [], total: 0 } }), read: async () => ({ code: 0, data: true }) };
  const names = ['reactive', 'watch', 'onBeforeUnmount', 'onShow', 'onHide', 'onUnload', 'sheep', 'getAccessToken', 'getTenantId', 'getAccountTenantId',
    'WorkbenchApi', ...Object.keys(helpers), 'setTimeout', 'clearTimeout'];
  const values = [x => x, (_get, changed) => { lifecycle.changed = changed; }, fn => { lifecycle.unmount = fn; },
    fn => { lifecycle.show = fn; }, fn => { lifecycle.hide = fn; }, fn => { lifecycle.unload = fn; },
    { $store: () => user, $router: { go: (...args) => navigations.push(args) } }, () => token, () => tenant, () => tenant, api,
    ...Object.values(helpers), fn => { timers.set(++timerId, fn); return timerId; }, id => timers.delete(id)];
  const workbench = new Function(...names, `${source}\nreturn useOrderWorkbench();`)(...values);
  return { workbench, lifecycle, api, requests, user, timers, navigations,
    setToken: next => { token = next; }, setTenant: next => { tenant = next; } };
}
test('polls use a fixed new-message baseline and replace counts instead of accumulating', async () => {
  const h = harness(); h.lifecycle.show(); await settle();
  h.api.summary = async params => { h.requests.push(params); return response({ cursor: '30', newCount: 3 }); };
  await h.workbench.refresh(); assert.equal(h.requests.at(-1).afterCursor, '20'); assert.equal(h.workbench.state.summary.newCount, 3);
  h.api.summary = async params => { h.requests.push(params); return response({ cursor: '35', newCount: 1 }); };
  await h.workbench.refresh(); assert.equal(h.requests.at(-1).afterCursor, '20'); assert.equal(h.workbench.state.summary.newCount, 1);
  h.lifecycle.unload();
});
test('a failed summary preserves actual last-good values and exposes stale state', async () => {
  const h = harness(); h.lifecycle.show(); await settle();
  h.api.summary = async () => { throw Error('offline'); }; await h.workbench.refresh();
  assert.equal(h.workbench.state.summary.pendingCount, 2); assert.ok(h.workbench.state.error); assert.ok(h.workbench.state.lastSuccess);
  h.lifecycle.unload();
});
test('old account response is discarded even when token changes without a reactive callback', async () => {
  const h = harness(), pending = deferred(); h.api.summary = () => pending.promise;
  h.lifecycle.show(); await settle(); h.setToken('token-B'); pending.resolve(response()); await settle();
  assert.equal(h.workbench.state.summary, null); h.lifecycle.unload();
});
test('switching tenant clears counters and suppresses a late old-tenant response', async () => {
  const h = harness(); h.lifecycle.show(); await settle(); const pending = deferred(); h.api.summary = () => pending.promise;
  const refresh = h.workbench.refresh(); h.setTenant(3000); h.lifecycle.changed();
  assert.equal(h.workbench.state.summary, null); h.lifecycle.hide(); pending.resolve(response()); await refresh;
  assert.equal(h.workbench.state.summary, null); h.lifecycle.unload();
});
test('hiding/unloading a page cancels timers and rejects pending response', async () => {
  const h = harness(), pending = deferred(); h.api.summary = () => pending.promise; h.lifecycle.show(); await settle();
  h.lifecycle.hide(); pending.resolve(response()); await settle(); assert.equal(h.workbench.state.summary, null);
  assert.equal(h.timers.size, 0); h.lifecycle.unload(); assert.equal(h.timers.size, 0);
});
test('read sends only exact unread event IDs, never a read-all cursor', async () => {
  const h = harness(); h.lifecycle.show(); await settle(); let actual;
  h.api.read = async ids => { actual = ids; return { code: 0 }; };
  assert.equal(await h.workbench.markRead([{ eventId: '15', unread: true }, { eventId: '15', unread: true }, { eventId: '16', unread: false }]), true);
  assert.deepEqual(actual, ['15']); assert.equal(h.workbench.state.summary.pendingCount, 2); h.lifecycle.unload();
});
test('account switch while read refreshes cannot navigate into the old account order', async () => {
  const h = harness(); h.lifecycle.show(); await settle(); const pending = deferred(); h.api.summary = () => pending.promise;
  const opening = h.workbench.open({ eventId: '15', action: 'RECEIVE', orderId: 3, unread: true }); await settle();
  h.setToken('token-B'); pending.resolve(response()); await opening; assert.deepEqual(h.navigations, []); h.lifecycle.unload();
});
test('many exact events are read in bounded batches instead of blocking order navigation', async () => {
  const h = harness(); h.lifecycle.show(); await settle(); const batches = [];
  h.api.read = async ids => { batches.push(ids); return { code: 0 }; };
  const entries = Array.from({ length: 201 }, (_, n) => ({ eventId: String(n + 1), unread: true }));
  assert.equal(await h.workbench.markRead(entries), true); assert.deepEqual(batches.map(x => x.length), [100, 100, 1]);
  assert.deepEqual(batches.flat(), entries.map(x => x.eventId)); h.lifecycle.unload();
});
test('reminder service failure does not block the existing order route for the same identity', async () => {
  const h = harness(); h.lifecycle.show(); await settle();
  h.workbench.state.summary.rowEntries = [{ eventId: '15', orderId: 3, unread: true }];
  h.api.read = async () => { throw Error('read service offline'); };
  assert.equal(await h.workbench.readOrder(3), true); assert.ok(h.workbench.state.pageError);
  h.lifecycle.unload();
});
test('viewing task list does not mark all events read and resets only banner baseline', async () => {
  const h = harness(); h.lifecycle.show(); await settle(); let writes = 0; h.api.read = async () => { writes++; return { code: 0 }; };
  await h.workbench.showTasks(); assert.equal(writes, 0); assert.equal(h.workbench.state.expanded, true); h.lifecycle.unload();
});
