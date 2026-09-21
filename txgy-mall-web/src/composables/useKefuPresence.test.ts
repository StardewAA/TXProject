import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ api: vi.fn(), epoch: 1, cleanup: [] as Array<() => void> }))
vi.mock('@/api/kefu-chat', () => ({ kefuChatApi: { heartbeat: mocks.api } }))
vi.mock('@/utils/tenant-context', () => ({ getTenantContext: () => ({ epoch: mocks.epoch }) }))
vi.mock('vue', async original => ({ ...await original<typeof import('vue')>(), onBeforeUnmount: (fn: () => void) => mocks.cleanup.push(fn) }))
import { useKefuPresence } from './useKefuPresence'
let documentTarget: EventTarget, windowTarget: EventTarget
const visibility = (value: string) => { Object.assign(documentTarget, { visibilityState: value }); documentTarget.dispatchEvent(new Event('visibilitychange')) }
const deferred = () => { let resolve!: (value: { online: boolean; expiresAt: number }) => void; const promise = new Promise<{ online: boolean; expiresAt: number }>(done => { resolve = done }); return { resolve, promise } }
beforeEach(() => {
  vi.useFakeTimers(); mocks.cleanup = []; mocks.epoch = 1; mocks.api.mockReset()
  documentTarget = new EventTarget(); Object.assign(documentTarget, { visibilityState: 'visible' })
  windowTarget = new EventTarget(); Object.assign(windowTarget, { setTimeout, clearTimeout })
  vi.stubGlobal('document', documentTarget); vi.stubGlobal('window', windowTarget)
  mocks.api.mockImplementation(async ({ online }) => ({ online, expiresAt: Date.now() + 90000 }))
})
afterEach(async () => { mocks.cleanup.forEach(fn => fn()); await vi.advanceTimersByTimeAsync(0); vi.useRealTimers(); vi.unstubAllGlobals() })
it('never declares presence until explicit opt-in; never used means no offline call either', async () => {
  const state = useKefuPresence({ scope: 'SHOP', shopId: 7 }); state.setOnline(false); visibility('hidden')
  mocks.cleanup.forEach(fn => fn()); mocks.cleanup = []; await vi.advanceTimersByTimeAsync(100000)
  expect(mocks.api).not.toHaveBeenCalled(); expect(state.online.value).toBe(false)
})
it('renews confirmed online at 30 seconds then stops and sends offline once', async () => {
  const state = useKefuPresence({ scope: 'SHOP', shopId: 7 }); state.setOnline(true); await vi.advanceTimersByTimeAsync(0)
  expect(state.online.value).toBe(true); await vi.advanceTimersByTimeAsync(30000); expect(mocks.api).toHaveBeenCalledTimes(2)
  state.setOnline(false); await vi.advanceTimersByTimeAsync(100000)
  expect(state.online.value).toBe(false); expect(mocks.api).toHaveBeenCalledTimes(3); expect(mocks.api.mock.calls[2]?.[0].online).toBe(false)
})
it('does not show online on missing or expired server confirmation', async () => {
  mocks.api.mockResolvedValue({ online: true, expiresAt: Date.now() - 1 }); const state = useKefuPresence({ scope: 'SHOP', shopId: 7 })
  state.setOnline(true); await vi.advanceTimersByTimeAsync(100000); expect(state.online.value).toBe(false); expect(mocks.api).toHaveBeenCalledTimes(1)
})
it('hiding the page explicitly goes offline and does not auto-online when visible again', async () => {
  const state = useKefuPresence({ scope: 'SHOP', shopId: 7 }); state.setOnline(true); await vi.advanceTimersByTimeAsync(0)
  visibility('hidden'); await vi.advanceTimersByTimeAsync(0); visibility('visible'); await vi.advanceTimersByTimeAsync(100000)
  expect(state.online.value).toBe(false); expect(mocks.api.mock.calls.map(call => call[0].online)).toEqual([true, false])
})
it('serializes offline behind an in-flight online request so delayed replies cannot resurrect online', async () => {
  const gate = deferred(); mocks.api.mockReturnValueOnce(gate.promise); const state = useKefuPresence({ scope: 'SHOP', shopId: 7 })
  state.setOnline(true); state.setOnline(false); expect(mocks.api).toHaveBeenCalledTimes(1)
  gate.resolve({ online: true, expiresAt: Date.now() + 90000 }); await vi.advanceTimersByTimeAsync(0)
  expect(mocks.api.mock.calls.map(call => call[0].online)).toEqual([true, false]); expect(state.online.value).toBe(false)
})
it('unmount while online request pending waits then sends offline and leaves no renewal timers', async () => {
  const gate = deferred(); mocks.api.mockReturnValueOnce(gate.promise); const state = useKefuPresence({ scope: 'SHOP', shopId: 7 })
  state.setOnline(true); mocks.cleanup.forEach(fn => fn()); mocks.cleanup = []
  gate.resolve({ online: true, expiresAt: Date.now() + 90000 }); await vi.advanceTimersByTimeAsync(100000)
  expect(mocks.api.mock.calls.map(call => call[0].online)).toEqual([true, false]); expect(state.online.value).toBe(false); expect(vi.getTimerCount()).toBe(0)
})
it('account replacement cannot emit an old scope heartbeat with new credentials', async () => {
  const gate = deferred(); mocks.api.mockReturnValueOnce(gate.promise); const state = useKefuPresence({ scope: 'SHOP', shopId: 7 })
  state.setOnline(true); mocks.epoch = 2; windowTarget.dispatchEvent(new Event('mall:tenant-context-updated'))
  gate.resolve({ online: true, expiresAt: Date.now() + 90000 }); await vi.advanceTimersByTimeAsync(100000); state.setOnline(false)
  expect(state.online.value).toBe(false); expect(mocks.api).toHaveBeenCalledTimes(1)
})
it('renewal failure stops online and requires explicit new opt-in', async () => {
  const state = useKefuPresence({ scope: 'SHOP', shopId: 7 }); state.setOnline(true); await vi.advanceTimersByTimeAsync(0)
  mocks.api.mockRejectedValue(new Error('坐席已停用')); await vi.advanceTimersByTimeAsync(90000)
  expect(state.online.value).toBe(false); expect(state.error.value).toContain('坐席已停用'); expect(mocks.api).toHaveBeenCalledTimes(3)
})
