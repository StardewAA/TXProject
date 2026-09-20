import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ api: vi.fn(), current: true, cleanup: [] as Array<() => void> }))
vi.mock('@/api/fulfillment', () => ({ fulfillmentApi: { getLogistics: mocks.api } }))
vi.mock('./useAccountOperation', () => ({ useAccountOperation: () => ({ current: () => mocks.current }) }))
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onBeforeUnmount: (fn: () => void) => mocks.cleanup.push(fn) }))
import { ref } from 'vue'
import { useOrderLogistics } from './useOrderLogistics'
import type { OrderLogistics } from '@/types/fulfillment'

const result = (orderId: number, extra = {}): OrderLogistics => ({ orderId, queryStatus: 'NOT_CONFIGURED', events: [], ...extra })
const deferred = () => {
  let resolve!: (value: OrderLogistics) => void
  const promise = new Promise<OrderLogistics>(yes => { resolve = yes })
  return { resolve, promise }
}
beforeEach(() => {
  vi.useFakeTimers(); mocks.current = true; mocks.cleanup = []; mocks.api.mockReset()
  vi.stubGlobal('window', { setInterval, clearInterval }); mocks.api.mockResolvedValue(result(1))
})
afterEach(() => { mocks.cleanup.forEach(fn => fn()); vi.useRealTimers(); vi.unstubAllGlobals() })

it('loads authenticated endpoint once and does not auto-poll the logistics provider', async () => {
  const state = useOrderLogistics(() => 1); await vi.advanceTimersByTimeAsync(0)
  expect(state.data.value?.queryStatus).toBe('NOT_CONFIGURED'); expect(state.loading.value).toBe(false)
  await vi.advanceTimersByTimeAsync(60000); expect(mocks.api).toHaveBeenCalledTimes(1)
})
it('honors server retry interval and ignores duplicate clicks', async () => {
  mocks.api.mockResolvedValue(result(1, { refreshAfterSeconds: 5 }))
  const state = useOrderLogistics(() => 1); await vi.advanceTimersByTimeAsync(0)
  await state.load(); expect(mocks.api).toHaveBeenCalledTimes(1)
  await vi.advanceTimersByTimeAsync(5000); await state.load(); expect(mocks.api).toHaveBeenCalledTimes(2)
})
it('discards a late response after route id changes and starts the new query immediately', async () => {
  const old = deferred(); mocks.api.mockReturnValueOnce(old.promise).mockResolvedValueOnce(result(2))
  const id = ref(1); const state = useOrderLogistics(() => id.value)
  id.value = 2; await vi.advanceTimersByTimeAsync(0)
  old.resolve(result(1)); await vi.advanceTimersByTimeAsync(0)
  expect(state.data.value?.orderId).toBe(2)
})
it('does not repaint or retry after an account change', async () => {
  const gate = deferred(); mocks.api.mockReturnValue(gate.promise)
  const state = useOrderLogistics(() => 1); mocks.current = false
  gate.resolve(result(1)); await vi.advanceTimersByTimeAsync(0); await state.load()
  expect(state.data.value).toBeNull(); expect(mocks.api).toHaveBeenCalledTimes(1)
})
it('rejects a response for another order rather than exposing it', async () => {
  mocks.api.mockResolvedValue(result(2)); const state = useOrderLogistics(() => 1)
  await vi.advanceTimersByTimeAsync(0)
  expect(state.data.value).toBeNull(); expect(state.failed.value).toBe(true)
})
it('a query error preserves the last snapshot explicitly as stale and permits controlled retry', async () => {
  const state = useOrderLogistics(() => 1); await vi.advanceTimersByTimeAsync(0)
  mocks.api.mockRejectedValueOnce(new Error('connection')); await state.load()
  expect(state.failed.value).toBe(true); expect(state.data.value?.stale).toBe(true)
  expect(state.canRefresh.value).toBe(false)
  await vi.advanceTimersByTimeAsync(2000); await state.load()
  expect(state.failed.value).toBe(false); expect(state.data.value?.stale).toBeUndefined()
})
it('unmount stops timer and invalidates pending query', async () => {
  const gate = deferred(); mocks.api.mockReturnValue(gate.promise)
  const state = useOrderLogistics(() => 1); mocks.cleanup.forEach(fn => fn()); mocks.cleanup = []
  gate.resolve(result(1)); await vi.advanceTimersByTimeAsync(0)
  expect(state.data.value).toBeNull(); expect(vi.getTimerCount()).toBe(0)
})
