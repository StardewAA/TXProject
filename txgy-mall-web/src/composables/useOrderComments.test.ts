import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ read: vi.fn(), send: vi.fn(), current: true, cleanup: [] as Array<() => void> }))
vi.mock('@/api/mall', () => ({ tradeApi: { getOrder: mocks.read, createComment: mocks.send } }))
vi.mock('./useAccountOperation', () => ({ useAccountOperation: () => ({ current: () => mocks.current }) }))
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onBeforeUnmount: (fn: () => void) => mocks.cleanup.push(fn) }))
import { useOrderComments } from './useOrderComments'
import type { TradeOrder } from '@/types/mall'
const order = (done: number[] = [], extra = {}): TradeOrder => ({ id: 42, status: 30, payPrice: 1000, commentStatus: false, items: [11, 12, 13].map(id => ({ id, count: 1, price: 1000, commentStatus: done.includes(id), spuName: `商品${id}` })), ...extra })
const fill = (state: ReturnType<typeof useOrderComments>) => state.forms.value.forEach(form => { form.content = `草稿${form.item.id}` })
const deferred = <T,>() => { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done }); return { promise, resolve } }
beforeEach(() => { mocks.current = true; mocks.cleanup = []; mocks.read.mockReset().mockResolvedValue(order()); mocks.send.mockReset().mockResolvedValue(1) })
afterEach(() => { mocks.cleanup.forEach(fn => fn()) })
it('only loads completed-order items explicitly unreviewed by the backend', async () => {
  mocks.read.mockResolvedValue(order([11])); const state = useOrderComments('42'); await state.load(); expect(state.forms.value.map(form => form.item.id)).toEqual([12, 13])
})
it.each([0, 10, 20, 40])('never exposes a comment form for order state %s', async status => {
  mocks.read.mockResolvedValue(order([], { status })); const state = useOrderComments('42'); await state.load(); expect(state.forms.value).toEqual([]); expect(await state.submit()).toBe(false); expect(mocks.send).not.toHaveBeenCalled()
})
it('already-reviewed and unknown states do not grant eligibility', async () => {
  mocks.read.mockResolvedValue(order([], { commentStatus: true })); const state = useOrderComments('42'); await state.load(); expect(state.forms.value).toEqual([])
  mocks.read.mockResolvedValue(order([], { commentStatus: undefined })); await state.load(); expect(state.forms.value).toEqual([]); expect(state.needsRefresh.value).toBe(true)
})
it('removes every successful item immediately, keeps remaining drafts, and retry never resends successes', async () => {
  const done = [11]; mocks.read.mockImplementation(async () => order(done)); mocks.send.mockImplementation(async ({ orderItemId }) => { if (orderItemId === 13 && !done.includes(12)) throw new Error('unexpected'); if (orderItemId === 13 && mocks.send.mock.calls.length === 2) throw new Error('暂时失败'); done.push(orderItemId); return 1 })
  const state = useOrderComments('42'); await state.load(); fill(state); expect(await state.submit()).toBe(false)
  expect(state.forms.value.map(form => form.item.id)).toEqual([13]); expect(state.forms.value[0]?.content).toBe('草稿13'); expect(state.error.value).toContain('已成功项目不会重发')
  expect(await state.submit()).toBe(true); expect(mocks.send.mock.calls.map(call => call[0].orderItemId)).toEqual([12, 13, 13])
})
it('preflight skips newly reviewed items from another device without overwriting remaining drafts', async () => {
  const state = useOrderComments('42'); await state.load(); fill(state); mocks.read.mockResolvedValue(order([11])); expect(await state.submit()).toBe(true)
  expect(mocks.send.mock.calls.map(call => call[0].orderItemId)).toEqual([12, 13])
})
it('lost acknowledgment followed by authoritative reviewed status does not resend the item', async () => {
  const done: number[] = []; mocks.read.mockImplementation(async () => order(done)); mocks.send.mockImplementationOnce(async ({ orderItemId }) => { done.push(orderItemId); throw new Error('ACK lost') })
  const state = useOrderComments('42'); await state.load(); fill(state); expect(await state.submit()).toBe(false); expect(state.forms.value.map(form => form.item.id)).toEqual([12, 13]); expect(await state.submit()).toBe(true)
  expect(mocks.send.mock.calls.map(call => call[0].orderItemId)).toEqual([11, 12, 13])
})
it('failed result reconciliation blocks retry until explicit successful refresh', async () => {
  const state = useOrderComments('42'); await state.load(); fill(state); mocks.read.mockResolvedValueOnce(order()).mockRejectedValueOnce(new Error('read failed')); mocks.send.mockRejectedValueOnce(new Error('write uncertain'))
  expect(await state.submit()).toBe(false); expect(state.needsRefresh.value).toBe(true); await state.submit(); expect(mocks.send).toHaveBeenCalledTimes(1)
  await state.load(); expect(state.needsRefresh.value).toBe(false); expect(state.forms.value[0]?.content).toBe('草稿11')
})
it('parallel submit clicks cannot start multiple batches', async () => {
  const state = useOrderComments('42'); await state.load(); fill(state); const gate = deferred<number>(); mocks.send.mockReturnValueOnce(gate.promise)
  const first = state.submit(); await Promise.resolve(); expect(await state.submit()).toBe(false); gate.resolve(1); expect(await first).toBe(true); expect(mocks.send).toHaveBeenCalledTimes(3)
})
it('account replacement or leaving page stops subsequent writes in a batch', async () => {
  const state = useOrderComments('42'); await state.load(); fill(state); const gate = deferred<number>(); mocks.send.mockReturnValueOnce(gate.promise)
  const first = state.submit(); await Promise.resolve(); mocks.current = false; mocks.cleanup.forEach(fn => fn()); mocks.cleanup = []; gate.resolve(1)
  expect(await first).toBe(false); expect(mocks.send).toHaveBeenCalledTimes(1); expect(state.forms.value).toEqual([])
})
it('wrong order response or blank content cannot publish a review', async () => {
  const state = useOrderComments('42'); await state.load(); expect(await state.submit()).toBe(false); expect(mocks.send).not.toHaveBeenCalled()
  mocks.read.mockResolvedValue(order([], { id: 88 })); await state.load(); expect(state.needsRefresh.value).toBe(true); expect(state.error.value).toContain('订单信息不一致')
})
it('a preflight status change cannot masquerade as a successful comment', async () => {
  const state = useOrderComments('42'); await state.load(); fill(state); mocks.read.mockResolvedValue(order([], { status: 40 }))
  expect(await state.submit()).toBe(false); expect(mocks.send).not.toHaveBeenCalled(); expect(state.error.value).toContain('订单状态已变化')
})
