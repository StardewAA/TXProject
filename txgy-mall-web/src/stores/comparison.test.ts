import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
const mocks = vi.hoisted(() => ({ token: '', context: { epoch: 0, browseTenantId: '1', accountTenantId: null as string | null, accountUserId: null as string | null }, list: vi.fn(), replace: vi.fn(), ensure: vi.fn() }))
vi.mock('@/api/comparison', () => ({ comparisonApi: { list: mocks.list, replace: mocks.replace } }))
vi.mock('@/api/http', () => ({ tokenStorage: { getAccess: () => mocks.token }, ensureAccountSession: mocks.ensure, StaleSessionError: class extends Error {} }))
vi.mock('@/utils/tenant-context', () => ({ getTenantContext: () => ({ ...mocks.context }) }))
import { useComparisonStore } from './comparison'
const saved = (skuIds = ['1'], version = 1) => ({ skuIds, version, quantity: 2, preference: 'PRICE' as const })
function login(user = '7') { mocks.token = 'test'; mocks.context = { ...mocks.context, epoch: mocks.context.epoch + 1, accountTenantId: '1', accountUserId: user }; window.dispatchEvent(new Event('mall:tenant-context-updated')) }
let storage: Map<string, string>
beforeEach(() => {
  mocks.token = ''; mocks.context = { epoch: 0, browseTenantId: '1', accountTenantId: null, accountUserId: null }
  mocks.list.mockReset().mockResolvedValue(saved()); mocks.replace.mockReset().mockImplementation(async (data: object) => ({ ...data, version: 2 })); mocks.ensure.mockReset().mockResolvedValue(undefined)
  storage = new Map(); vi.stubGlobal('window', new EventTarget()); vi.stubGlobal('localStorage', { getItem: (key: string) => storage.get(key) ?? null, setItem: (key: string, value: string) => storage.set(key, value), removeItem: (key: string) => storage.delete(key) })
  setActivePinia(createPinia())
})
afterEach(() => vi.unstubAllGlobals())
it('guest persists identifiers only without calling the private list API', async () => {
  const store = useComparisonStore(); await store.add('11'); expect(store.ids).toEqual(['11']); expect(JSON.parse(storage.get('mall-pc-comparison-v1:1')!)).toEqual(['11']); expect(mocks.replace).not.toHaveBeenCalled()
})
it('login never merges guest items until explicit confirmation', async () => {
  const store = useComparisonStore(); await store.add('11'); login(); await store.refresh()
  expect(store.ids).toEqual(['1']); expect(store.hasAnonymousMerge).toBe(true); expect(mocks.replace).not.toHaveBeenCalled()
  await store.mergeAnonymous(); expect(mocks.replace).toHaveBeenCalledWith({ version: 1, skuIds: ['1', '11'], quantity: 2, preference: 'PRICE' }); expect(store.hasAnonymousMerge).toBe(false)
})
it('a changed server revision fails closed instead of silently retrying an overwrite', async () => {
  login(); const store = useComparisonStore(); await store.refresh(); mocks.replace.mockRejectedValueOnce(new Error('409 对比清单已更新'))
  await expect(store.add('2')).rejects.toThrow('409'); expect(store.ids).toEqual(['1']); expect(store.ready).toBe(false); expect(mocks.replace).toHaveBeenCalledTimes(1)
})
it('logout clears saved member selections and only restores independent guest selection', async () => {
  storage.set('mall-pc-comparison-v1:1', '["11"]'); login(); const store = useComparisonStore(); await store.refresh()
  mocks.token = ''; mocks.context = { ...mocks.context, epoch: 3, accountTenantId: null, accountUserId: null }; window.dispatchEvent(new Event('mall:tenant-context-updated'))
  expect(store.ids).toEqual(['11']); expect(storage.get('mall-pc-comparison-v1:1')).toBe('["11"]')
})
it('late private list cannot expose old account data after logout', async () => {
  let resolve!: (value: ReturnType<typeof saved>) => void
  mocks.list.mockReturnValue(new Promise(yes => { resolve = yes })); login(); const store = useComparisonStore(); const pending = store.refresh(); await Promise.resolve(); await Promise.resolve()
  mocks.token = ''; mocks.context = { ...mocks.context, epoch: 3, accountTenantId: null, accountUserId: null }; window.dispatchEvent(new Event('mall:tenant-context-updated')); resolve(saved(['999']))
  await pending; expect(store.ids).toEqual([]); expect(store.error).toBe('')
})
it('invalid server quantity does not partially replace local selection', async () => {
  login(); const store = useComparisonStore(); mocks.list.mockResolvedValue({ ...saved(), quantity: 0 }); await store.refresh(); expect(store.ready).toBe(false); expect(store.ids).toEqual([])
})
it('late write cannot restore the previous account or clear its independent guest basket', async () => {
  let resolve!: (value: ReturnType<typeof saved>) => void
  login(); storage.set('mall-pc-comparison-v1:1', '["11"]'); const store = useComparisonStore(); await store.refresh()
  mocks.replace.mockReturnValueOnce(new Promise(yes => { resolve = yes })); const pending = store.mergeAnonymous(); const rejected = expect(pending).rejects.toBeInstanceOf(Error)
  mocks.list.mockResolvedValue(saved(['88'], 8)); login('8'); await store.refresh(); resolve(saved(['1', '11'], 2)); await rejected
  expect(store.ids).toEqual(['88']); expect(store.version).toBe(8); expect(storage.get('mall-pc-comparison-v1:1')).toBe('["11"]')
})
it('over-limit merge leaves both server list and guest ids unchanged', async () => {
  login(); storage.set('mall-pc-comparison-v1:1', '["4","5"]'); mocks.list.mockResolvedValue(saved(['1', '2', '3'])); const store = useComparisonStore(); await store.refresh()
  await expect(store.mergeAnonymous()).rejects.toThrow('最多'); expect(mocks.replace).not.toHaveBeenCalled(); expect(store.ids).toEqual(['1', '2', '3']); expect(store.anonymousIds).toEqual(['4', '5'])
})
it('a token refresh for the same verified account does not clear or refetch the saved selection', async () => {
  login(); const store = useComparisonStore(); await store.refresh(); mocks.list.mockClear(); mocks.token = 'refreshed-token'; window.dispatchEvent(new Event('mall:token-updated'))
  expect(store.ids).toEqual(['1']); expect(store.ready).toBe(true); expect(mocks.list).not.toHaveBeenCalled()
})
it('browser storage denial leaves guest selection unchanged and never falls back to a server write', async () => {
  const store = useComparisonStore(); vi.stubGlobal('localStorage', { getItem: () => null, setItem: () => { throw new Error('denied') } })
  await expect(store.add('11')).rejects.toThrow('本地存储'); expect(store.ids).toEqual([]); expect(mocks.replace).not.toHaveBeenCalled()
})
it('a cross-tab storage clear also clears the anonymous basket in memory', async () => {
  const store = useComparisonStore(); await store.add('11'); storage.clear(); const event = new Event('storage'); Object.defineProperty(event, 'key', { value: null }); window.dispatchEvent(event)
  expect(store.ids).toEqual([]); expect(store.anonymousIds).toEqual([])
})
