import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createOrderCommentFlow } from '../sheep/helper/order-comment-flow.mjs';
function setup() {
  const state = { id: '42', orderInfo: { items: [] }, commentList: [], loading: false, submitting: false, needsRefresh: false, error: '' };
  const done = new Set(), calls = []; let current = true, failWrite = 0, failRead = false, status = 30;
  const order = () => ({ id: 42, status, commentStatus: false, items: [11, 12, 13].map(id => ({ id, commentStatus: done.has(id), spuName: `商品${id}` })) });
  const api = { readOrder: async () => { if (failRead) throw new Error('read failed'); return order(); }, sendComment: async value => { calls.push(value); if (failWrite === Number(value.orderItemId)) throw new Error('write failed'); done.add(Number(value.orderItemId)); }, isCurrent: () => current };
  const flow = createOrderCommentFlow(state, { readOrder: id => api.readOrder(id), sendComment: value => api.sendComment(value), isCurrent: () => api.isCurrent() });
  const fill = () => state.commentList.forEach(comment => { comment.content = `草稿${comment.orderItemId}`; });
  return { state, calls, done, api, flow, fill, set current(value) { current = value; }, set failWrite(value) { failWrite = value; }, set failRead(value) { failRead = value; }, set status(value) { status = value; } };
}
test('loads only explicitly unreviewed items and keeps matching render and draft indexes', async () => { const s = setup(); s.done.add(11); await s.flow.load(); assert.deepEqual(s.state.orderInfo.items.map(item => item.id), [12, 13]); assert.deepEqual(s.state.commentList.map(item => item.orderItemId), ['12', '13']); });
for (const status of [0, 10, 20, 40]) test(`status ${status} cannot be reviewed`, async () => { const s = setup(); s.status = status; await s.flow.load(); assert.equal(s.state.commentList.length, 0); assert.equal(await s.flow.submit(), false); });
test('partial success removes the successful row, keeps remaining drafts, retries only unreviewed items', async () => {
  const s = setup(); await s.flow.load(); s.fill(); s.failWrite = 12; assert.equal(await s.flow.submit(), false); assert.deepEqual(s.state.commentList.map(item => item.orderItemId), ['12', '13']); assert.equal(s.state.commentList[0].content, '草稿12');
  s.failWrite = 0; assert.equal(await s.flow.submit(), true); assert.deepEqual(s.calls.map(value => value.orderItemId), ['11', '12', '12', '13']);
});
test('another device comment is skipped by read-only preflight', async () => { const s = setup(); await s.flow.load(); s.fill(); s.done.add(11); assert.equal(await s.flow.submit(), true); assert.deepEqual(s.calls.map(value => value.orderItemId), ['12', '13']); });
test('lost ACK followed by reviewed server status does not duplicate the successful comment', async () => {
  const s = setup(); await s.flow.load(); s.fill(); const sender = s.api.sendComment; s.api.sendComment = async value => { await sender(value); throw new Error('ACK lost'); };
  assert.equal(await s.flow.submit(), false); assert.deepEqual(s.state.commentList.map(value => value.orderItemId), ['12', '13']); s.api.sendComment = sender;
});
test('failed reconciliation stops writes until an explicit successful refresh', async () => {
  const s = setup(); await s.flow.load(); s.fill(); s.api.sendComment = async value => { s.calls.push(value); s.failRead = true; throw new Error('write uncertain'); };
  assert.equal(await s.flow.submit(), false); assert.equal(s.state.needsRefresh, true); await s.flow.submit(); assert.equal(s.calls.length, 1); s.failRead = false; await s.flow.load(); assert.equal(s.state.needsRefresh, false); assert.equal(s.state.commentList[0].content, '草稿11');
});
test('duplicate submit clicks are ignored and account change stops the rest of batch', async () => {
  const s = setup(); await s.flow.load(); s.fill(); let release; const pending = new Promise(done => { release = done; }); s.api.sendComment = async value => { s.calls.push(value); await pending; };
  const first = s.flow.submit(); await Promise.resolve(); assert.equal(await s.flow.submit(), false); s.current = false; s.flow.dispose(); release(); assert.equal(await first, false); assert.equal(s.calls.length, 1); assert.equal(s.state.commentList.length, 0);
});
test('blank content is not silently converted to a five-star review', async () => { const s = setup(); await s.flow.load(); assert.equal(await s.flow.submit(), false); assert.equal(s.calls.length, 0); });
test('status change during preflight is not reported as a successful comment', async () => { const s = setup(); await s.flow.load(); s.fill(); s.status = 40; assert.equal(await s.flow.submit(), false); assert.equal(s.calls.length, 0); assert.match(s.state.error, /订单状态已变化/); });
