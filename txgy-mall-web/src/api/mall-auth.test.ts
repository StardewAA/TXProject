import { beforeEach, it, expect, vi } from 'vitest'
const mocks = vi.hoisted(() => ({ transport: vi.fn(), refresh: vi.fn(), message: vi.fn() }))
vi.mock('axios', async original => {
  const actual = await original<typeof import('axios')>()
  return { ...actual, default: { ...actual.default, create: () => ({ request: mocks.transport }), post: mocks.refresh } }
})
vi.mock('element-plus', () => ({ ElMessage: { error: mocks.message } }))
const ok = (data: unknown) => ({ data: { code: 0, data } })
const config = { protocol: 'challenge-v1', tenantId: 1, available: true, registrationAvailable: true, tenantSelection: false }
const identity = { tenantId: 1, userId: 7, accessToken: 'new-access', refreshToken: 'new-refresh' }
const deferred = () => { let resolve!: (value: ReturnType<typeof ok>) => void
  const promise = new Promise<ReturnType<typeof ok>>(yes => { resolve = yes }); return { promise, resolve } }
beforeEach(async () => {
  vi.resetModules(); mocks.transport.mockReset(); mocks.refresh.mockReset(); mocks.message.mockReset()
  const values = new Map<string, string>()
  vi.stubGlobal('window', new EventTarget())
  vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value), removeItem: (key: string) => values.delete(key) })
  const { createPinia, setActivePinia } = await import('pinia'); setActivePinia(createPinia())
})
it('explicit target config is requested without original tenant or token', async () => {
  const { tokenStorage } = await import('./http'); tokenStorage.set('previous', 'previous-refresh')
  const { getAuthEntry } = await import('./mall-auth'); mocks.transport.mockResolvedValue(ok({ ...config, tenantId: 9001 }))
  expect((await getAuthEntry('9001')).tenantId).toBe('9001')
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.params).toEqual({ tenantId: '9001' }); expect(call.headers.get('Authorization')).toBeUndefined()
  expect(call.headers.get('tenant-id')).toBeUndefined(); expect(tokenStorage.getAccess()).toBe('previous')
})
it('foreign config 404 cannot degrade to original tenant authentication', async () => {
  const { getAuthEntry } = await import('./mall-auth'); mocks.transport.mockResolvedValue({ data: { code: 404 } })
  await expect(getAuthEntry('9001')).rejects.toThrow(); expect(mocks.transport).toHaveBeenCalledTimes(1)
})
it.each([{ tenantId: 88, userId: 7 }, { tenantId: 1, userId: 8 }, null])('candidate session mismatch cannot replace old token: %j', async proof => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  tokenStorage.set('old', 'old-refresh')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/session') ? proof : identity))
  await expect(useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true)).rejects.toThrow()
  expect(tokenStorage.getAccess()).toBe('old')
  const verifier = mocks.transport.mock.calls[1]![0]
  expect(verifier.headers.get('Authorization')).toBe('Bearer new-access'); expect(verifier.headers.get('tenant-id')).toBe('1')
})
it('session verification 404 does not permit token commit or legacy fallback', async () => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  mocks.transport.mockImplementation(async input => input.url.endsWith('/session') ? { data: { code: 404 } } : ok(identity))
  await expect(useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true)).rejects.toThrow()
  expect(tokenStorage.getAccess()).toBe(''); expect(mocks.transport).toHaveBeenCalledTimes(2)
})
it('form invalidation while proof is pending preserves the old session', async () => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  tokenStorage.set('old', 'old-refresh'); let current = true; const proof = deferred()
  mocks.transport.mockImplementation(async input => input.url.endsWith('/session') ? proof.promise : ok(identity))
  const pending = useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true, () => current).catch(error => error)
  for (let n = 0; n < 15; n++) await Promise.resolve()
  expect(mocks.transport).toHaveBeenCalledTimes(2); current = false; proof.resolve(ok({ tenantId: 1, userId: 7 }))
  expect(await pending).toBeInstanceOf(Error); expect(tokenStorage.getAccess()).toBe('old')
})
it('protocol discovery is anonymous without tenant or token', async () => {
  const { tokenStorage } = await import('./http'); tokenStorage.set('previous', 'previous-refresh')
  const { getAuthEntry } = await import('./mall-auth'); mocks.transport.mockResolvedValue(ok(config))
  expect((await getAuthEntry()).protocol).toBe('challenge-v1')
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.headers.get('Authorization')).toBeUndefined(); expect(call.headers.get('tenant-id')).toBeUndefined()
})
it('registration directory never sends old session or tenant headers', async () => {
  const { tokenStorage } = await import('./http'); tokenStorage.set('previous', 'previous-refresh')
  const { getRegistrationOptions } = await import('./mall-auth')
  mocks.transport.mockResolvedValue(ok({ enabled: true, publicTenantId: null, tenants: [{ tenantId: 9001, name: '企业商城' }], crossTenantReady: false, selectionRequired: true }))
  const options = await getRegistrationOptions()
  expect(options.publicTenantId).toBeNull()
  expect(options.selectionRequired).toBe(true)
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.url).toBe('/member/mall-auth/registration-options')
  expect(call.headers.get('Authorization')).toBeUndefined(); expect(call.headers.get('tenant-id')).toBeUndefined()
})
it.each([404,500])('registration directory only permits absent-endpoint compatibility: %s', async status => {
  const { getRegistrationOptions } = await import('./mall-auth')
  mocks.transport.mockResolvedValue({ data: { code: status } })
  if(status===404) expect((await getRegistrationOptions()).enabled).toBe(false)
  else await expect(getRegistrationOptions()).rejects.toThrow()
  expect(mocks.transport).toHaveBeenCalledTimes(1)
})
it.each([404, 500, 'offline', 'invalid'])('only absent config 404 permits legacy compatibility: %s', async status => {
  const { getAuthEntry } = await import('./mall-auth')
  const { AxiosError } = await import('axios')
  if (typeof status === 'number') mocks.transport.mockRejectedValue(new AxiosError('failed', undefined, undefined, undefined, { status } as never))
  else if (status === 'offline') mocks.transport.mockRejectedValue(new Error('offline'))
  else mocks.transport.mockResolvedValue(ok({ ...config, tenantId: 88 }))
  if (status === 404) expect((await getAuthEntry()).protocol).toBe('legacy')
  else await expect(getAuthEntry()).rejects.toThrow()
  expect(mocks.transport).toHaveBeenCalledTimes(1)
})
it('new password login sends explicit tenant without old auth header', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  const { tokenStorage } = await import('./http'); tokenStorage.set('old', 'old-refresh')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/login') ? identity : { tenantId: 1, id: 7, userId: 7 }))
  await useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true)
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.url).toBe('/member/mall-auth/login'); expect(call.headers.get('Authorization')).toBeUndefined()
  expect(call.headers.get('tenant-id')).toBe('1'); expect(call.data.tenantId).toBe('1')
  expect(tokenStorage.getAccess()).toBe('new-access')
})
it('new registration sends challenge and never calls old registration', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/register') ? identity : { tenantId: 1, id: 7, userId: 7 }))
  await useAuthStore().register({ mobile: '19900000000', password: 'Password123', code: '123456', nickname: '会员', agree: true,
    memberIdentity: 'buyer', tenantId: '1', challengeId: 'a'.repeat(32) }, true)
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.url).toBe('/member/mall-auth/register'); expect(call.data.challengeId).toBe('a'.repeat(32))
  expect(mocks.transport.mock.calls.some(([input]) => input.url === '/member/auth/register')).toBe(false)
})
it('legacy registration compatibility fails closed without sending credentials', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  await expect(useAuthStore().register({ mobile: '19900000000', password: 'Password123', code: '123456', nickname: '会员', agree: true,
    memberIdentity: 'buyer' }, false)).rejects.toThrow('旧版注册入口已关闭')
  expect(mocks.transport).not.toHaveBeenCalled()
})
it.each([undefined, 88])('challenge response with tenant %s cannot write a session', async tenantId => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  mocks.transport.mockResolvedValue(ok({ ...identity, tenantId }))
  await expect(useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true)).rejects.toThrow('租户')
  expect(tokenStorage.getAccess()).toBe(''); expect(mocks.transport).toHaveBeenCalledTimes(1)
})
it('new login 404 never retries old protocol', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  mocks.transport.mockResolvedValue({ data: { code: 404, msg: 'not found' } })
  await expect(useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true)).rejects.toThrow()
  expect(mocks.transport).toHaveBeenCalledTimes(1); expect(mocks.refresh).not.toHaveBeenCalled()
})
it('form invalidation rejects pending login before writing token', async () => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  let current = true; const pending = deferred(); mocks.transport.mockReturnValue(pending.promise)
  const login = useAuthStore().login({ mobile: '19900000000', password: 'Password123', tenantId: '1' }, true, () => current).catch(error => error)
  current = false; pending.resolve(ok(identity)); expect(await login).toBeInstanceOf(Error)
  expect(tokenStorage.getAccess()).toBe(''); expect(mocks.transport).toHaveBeenCalledTimes(1)
})

it('image registration uses only the new operation and verifies its session before commit', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  const { tokenStorage } = await import('./http')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/register-image') ? identity : { tenantId: 1, id: 7, userId: 7 }))
  await useAuthStore().registerImage({ tenantId: '1', loginName: 'Buyer_1', password: 'Password123', confirmPassword: 'Password123',
    captchaSession: 'b'.repeat(32), imageChallengeId: 'a'.repeat(32), imageCode: 'ab2cd', agree: true, memberIdentity: 'supplier' })
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.url).toBe('/member/mall-auth/register-image')
  expect(call.data).toMatchObject({ loginName: 'buyer_1', imageCode: 'AB2CD', memberIdentity: 'supplier' })
  expect(call.data).not.toHaveProperty('mobile'); expect(call.data).not.toHaveProperty('contactMobile')
  expect(call.headers.get('Authorization')).toBeUndefined()
  expect(mocks.transport.mock.calls[1]![0].url).toBe('/member/mall-tenant/session')
  expect(tokenStorage.getAccess()).toBe('new-access')
})
it.each(['buyer_1', '19900000000'])('account login preserves password authentication for %s', async account => {
  const { useAuthStore } = await import('@/stores/auth')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/account-login') ? identity : { tenantId: 1, id: 7, userId: 7 }))
  await useAuthStore().accountLogin({ tenantId: '1', account, password: '1234' })
  const call = mocks.transport.mock.calls[0]![0]
  expect(call.url).toBe('/member/mall-auth/account-login')
  expect(call.data).toEqual({ tenantId: '1', account, password: '1234' })
  expect(call.headers.get('Authorization')).toBeUndefined()
})
it('platform login derives its source tenant on the server and never sends a tenant hint', async () => {
  const platformIdentity = { ...identity, tenantId: 2009, userId: 77 }
  const { useAuthStore } = await import('@/stores/auth')
  const { tokenStorage } = await import('./http')
  mocks.transport.mockImplementation(async input => {
    if (input.url.endsWith('/platform-login')) return ok(platformIdentity)
    if (input.url.endsWith('/platform-account/session')) return ok({
      platformAccountId: 31, platformNo: 'PA-31', canonicalMemberId: 77, legacyMemberId: 77,
      sourceTenantId: 2009, memberships: [],
    })
    if (input.url.endsWith('/member/user/get')) return ok({ id: 77, tenantId: 2009, nickname: '平台会员' })
    throw new Error(`unexpected request: ${input.url}`)
  })
  await useAuthStore().platformLogin({ account: 'BUYER_1', password: '1234' })
  const login = mocks.transport.mock.calls[0]![0]
  expect(login.url).toBe('/member/mall-auth/platform-login')
  expect(login.data).toEqual({ account: 'buyer_1', password: '1234' })
  expect(login.data).not.toHaveProperty('tenantId')
  expect(login.headers.get('tenant-id')).toBeUndefined()
  const proof = mocks.transport.mock.calls[1]![0]
  expect(proof.url).toBe('/member/platform-account/session')
  expect(proof.headers.get('tenant-id')).toBeUndefined()
  expect(proof.headers.get('Authorization')).toBe('Bearer new-access')
  expect(tokenStorage.getAccess()).toBe('new-access')
})
it('platform login rejects a mismatched platform session before replacing the old token', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  const { tokenStorage } = await import('./http')
  tokenStorage.set('old', 'old-refresh')
  mocks.transport.mockImplementation(async input => input.url.endsWith('/platform-login')
    ? ok({ ...identity, tenantId: 2009, userId: 77 })
    : ok({ platformAccountId: 31, canonicalMemberId: 88, sourceTenantId: 2009 }))
  await expect(useAuthStore().platformLogin({ account: 'buyer_1', password: '1234' })).rejects.toThrow('平台账号会话')
  expect(tokenStorage.getAccess()).toBe('old')
  expect(mocks.transport).toHaveBeenCalledTimes(2)
})
it('new operation failures cannot fall back to SMS or legacy registration', async () => {
  const { useAuthStore } = await import('@/stores/auth')
  mocks.transport.mockResolvedValue({ data: { code: 404 } })
  await expect(useAuthStore().registerImage({ tenantId: '1', loginName: 'buyer_1', password: 'Password123', confirmPassword: 'Password123',
    captchaSession: 'b'.repeat(32), imageChallengeId: 'a'.repeat(32), imageCode: 'ab2cd', agree: true, memberIdentity: 'buyer' })).rejects.toThrow()
  expect(mocks.transport).toHaveBeenCalledTimes(1)
  expect(mocks.transport.mock.calls[0]![0].url).toBe('/member/mall-auth/register-image')
})
it('image registration cannot commit an unverified candidate session', async () => {
  const { useAuthStore } = await import('@/stores/auth'); const { tokenStorage } = await import('./http')
  tokenStorage.set('old', 'old-refresh')
  mocks.transport.mockImplementation(async input => ok(input.url.endsWith('/register-image') ? identity : { tenantId: 88, userId: 7 }))
  await expect(useAuthStore().registerImage({ tenantId: '1', loginName: 'buyer_1', password: 'Password123', confirmPassword: 'Password123',
    captchaSession: 'b'.repeat(32), imageChallengeId: 'a'.repeat(32), imageCode: 'ab2cd', agree: true, memberIdentity: 'buyer' })).rejects.toThrow('请勿重复注册')
  expect(tokenStorage.getAccess()).toBe('old'); expect(mocks.transport).toHaveBeenCalledTimes(2)
})
it('image request is anonymous and cannot return a foreign image challenge', async () => {
  const { challengeRequest } = await import('./mall-auth')
  const input = { tenantId: '1', loginName: 'buyer_1', captchaSession: 'b'.repeat(32), purpose: 'REGISTER' }
  mocks.transport.mockResolvedValue(ok({ tenantId: 2, challengeId: 'a'.repeat(32), imageDataUrl: 'data:image/png;base64,aGVsbG8=', expiresIn: 300, retryAfter: 2 }))
  await expect(challengeRequest('image-challenge', input)).rejects.toThrow()
  expect(mocks.transport.mock.calls[0]![0].headers.get('Authorization')).toBeUndefined()
  expect(mocks.transport).toHaveBeenCalledTimes(1)
})
