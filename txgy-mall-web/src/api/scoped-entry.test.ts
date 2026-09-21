import { afterEach, beforeEach, expect, it, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ transport: vi.fn(), refresh: vi.fn() }))
vi.mock('axios', async original => {
  const actual = await original<typeof import('axios')>()
  return { ...actual, default: { ...actual.default, create: () => ({ request: mocks.transport }), post: mocks.refresh } }
})
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))
const directory = { enabled: true, legacyTenantId: 1, publicTenantId: 9001,
  tenants: [{ tenantId: 9001, name: '隔离测试商城', registrationEnabled: false }], capabilities: { scopedEntry: true, browseSwitch: false, registrationSelection: false } }
const ok = (data: unknown) => ({ data: { code: 0, data } })
let values: Map<string, string>, tab: Map<string, string>
beforeEach(() => {
  vi.resetModules(); mocks.transport.mockReset(); mocks.refresh.mockReset()
  values = new Map([['mall-pc-access-token', 'LEGACY'], ['mall-pc-refresh-token', 'LEGACY-R']]); tab = new Map([['mall-scoped-entry', '9001']])
  const storage = (map: Map<string, string>) => ({ getItem: (key: string) => map.get(key) ?? null, setItem: (key: string, value: string) => map.set(key, value), removeItem: (key: string) => map.delete(key) })
  vi.stubGlobal('localStorage', storage(values)); vi.stubGlobal('sessionStorage', storage(tab)); vi.stubGlobal('window', new EventTarget())
  mocks.transport.mockImplementation(async config => config.url.endsWith('/config') ? ok(directory)
    : config.url.endsWith('/session') ? ok({ tenantId: 9001, userId: 9 }) : ok({ id: 9, nickname: '同租户会员' }))
})
afterEach(() => vi.unstubAllGlobals())
it('foreign anonymous entry never reads legacy token, cart writes attach only target token after proof', async () => {
  const { request, tokenStorage } = await import('./http')
  expect(tokenStorage.getAccess()).toBe('')
  const { productImage } = await import('@/utils/format')
  expect(productImage(910001, 'https://test/foreign.jpg')).toBe('https://test/foreign.jpg')
  expect(productImage(940215, 'https://test/wrong-product.jpg')).toBe('/product-images/940215-chlorinated-rubber-topcoat.png')
  expect(productImage(940207, 'https://test/wrong-pipe.jpg')).toBe('/product-images/kedun-real/preinsulated-pipe.png')
  await request({ url: '/product/spu/page', scope: 'browse' })
  let call = mocks.transport.mock.calls.at(-1)![0]
  expect(call.headers.get('tenant-id')).toBe('9001'); expect(call.headers.get('Authorization')).toBeUndefined()
  tokenStorage.set('FOREIGN', 'FOREIGN-R')
  await request({ url: '/trade/cart/add', method: 'POST', data: { skuId: 7 } })
  call = mocks.transport.mock.calls.at(-1)![0]
  expect(call.headers.get('tenant-id')).toBe('9001'); expect(call.headers.get('Authorization')).toBe('Bearer FOREIGN')
  const proof = mocks.transport.mock.calls.find(([c]) => c.url.endsWith('/session'))![0]
  expect(proof.headers.get('Authorization')).toBe('Bearer FOREIGN')
  tokenStorage.clear(); expect(values.get('mall-pc-access-token')).toBe('LEGACY'); expect(values.has('mall-pc-access-token:tenant:9001')).toBe(false)
})
it.each(['disabled', 'revoked', 'unavailable', 'wrongLegacy'])('foreign %s directory prevents any business dispatch and retains legacy storage', async mode => {
  const config = structuredClone(directory)
  if (mode === 'disabled') config.capabilities.scopedEntry = false
  if (mode === 'revoked') config.tenants = []
  if (mode === 'wrongLegacy') config.legacyTenantId = 2
  mocks.transport.mockResolvedValue(mode === 'unavailable' ? { data: { code: 503 } } : ok(config))
  const { request } = await import('./http')
  await expect(request({ url: '/product/spu/get-detail', scope: 'browse', params: { id: 7 } })).rejects.toThrow()
  expect(mocks.transport.mock.calls.every(([c]) => c.url === '/member/mall-tenant/config')).toBe(true)
  expect(values.get('mall-pc-access-token')).toBe('LEGACY')
})
it('revocation is rechecked after a successful read, not cached for the whole session', async () => {
  const { request } = await import('./http')
  await request({ url: '/product/spu/page', scope: 'browse' })
  mocks.transport.mockResolvedValue(ok({ ...directory, tenants: [] }))
  await expect(request({ url: '/trade/order/create', method: 'POST' })).rejects.toThrow()
  expect(mocks.transport.mock.calls.some(([c]) => c.url === '/trade/order/create')).toBe(false)
})
it('foreign legacy authentication cannot dispatch even when directory is enabled', async () => {
  const { request } = await import('./http')
  await expect(request({ url: '/member/auth/login', scope: 'auth' })).rejects.toThrow('不能回退旧版认证')
  expect(mocks.transport).not.toHaveBeenCalled()
})
it('malformed tab hint fails closed without sending legacy token', async () => {
  tab.set('mall-scoped-entry', 'bad')
  const { request } = await import('./http')
  await expect(request({ url: '/member/user/get' })).rejects.toThrow('入口参数无效')
  expect(mocks.transport).not.toHaveBeenCalled()
})
it('verified foreign profile may omit tenantId in the existing VO but must match session member', async () => {
  const { setActivePinia, createPinia } = await import('pinia'); setActivePinia(createPinia())
  const { tokenStorage } = await import('./http'); tokenStorage.set('FOREIGN', 'FOREIGN-R')
  const { useAuthStore } = await import('@/stores/auth'); const auth = useAuthStore()
  await auth.restoreSession(); expect(auth.profile?.id).toBe(9); expect(tokenStorage.getAccess()).toBe('FOREIGN')
  mocks.transport.mockImplementation(async config => config.url.endsWith('/config') ? ok(directory) : ok({ id: 10 }))
  await auth.loadProfile(); expect(tokenStorage.getAccess()).toBe(''); expect(values.get('mall-pc-access-token')).toBe('LEGACY')
})
