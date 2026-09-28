import { beforeEach, describe, expect, it, vi } from 'vitest'

describe('租户底座关闭能力与可信身份', () => {
  beforeEach(() => {
    vi.resetModules()
    const values = new Map<string, string>()
    vi.stubGlobal('window', new EventTarget())
    vi.stubGlobal('localStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) })
    vi.stubGlobal('sessionStorage', { getItem: () => null, setItem: () => undefined })
  })
  it('忽略本地伪造账号租户及浏览租户，不提供切换入口', async () => {
    localStorage.setItem('mall-pc-tenant-context', JSON.stringify({ browseTenantId: '88', accountTenantId: '88' }))
    const context = await import('./tenant-context')
    expect(context.getTenantContext()).toMatchObject({ browseTenantId: '1', accountTenantId: null, canSwitchBrowse: false })
    expect('setBrowseTenant' in context).toBe(false)
    expect(context.getAccountRequestTenant()).toBe('1')
    expect(context.getRegistrationEntryPath()).toBe('/register')
  })
  it('有目录不代表可切换，即使服务端提前宣告能力也不开放未完成功能', async () => {
    const context = await import('./tenant-context')
    context.acceptTenantCatalog({ enabled: true, legacyTenantId: 1, publicTenantId: 88,
      tenants: [{ tenantId: 88, name: '示例商城', registrationEnabled: true }], capabilities: { browseSwitch: true, registrationSelection: true } })
    expect(context.getTenantContext()).toMatchObject({ browseTenantId: '1', accountTenantId: null, canSwitchBrowse: false,
      canSelectRegistrationTenant: false, catalog: { capabilities: { browseSwitch: false, registrationSelection: false } } })
    expect(context.getRegistrationEntryTenant()).toBe('88')
    expect(context.getRegistrationEntryPath()).toBe('/register?tenantId=88&registrationTenant=88')
    expect(Object.isFrozen(context.getTenantContext().catalog?.tenants)).toBe(true)
  })
  it('账号只接受当前代次服务端确认且必须匹配原入口', async () => {
    const context = await import('./tenant-context')
    const original = context.getTenantContext().epoch
    expect(() => context.confirmAccountContext({ tenantId: 88, userId: 3 }, original)).toThrow('账号租户')
    expect(context.getTenantContext().accountTenantId).toBeNull()
    expect(context.confirmAccountContext({ tenantId: 1, userId: 3 }, original)).toBe(true)
    expect(context.getTenantContext()).toMatchObject({ accountTenantId: '1', accountUserId: '3' })
    context.invalidateAccountContext()
    expect(context.confirmAccountContext({ tenantId: 1, userId: 3 }, original)).toBe(false)
    expect(context.getTenantContext().accountTenantId).toBeNull()
  })
  it('平台账号租户只作为待验证恢复候选，服务端证明后才成为账号请求租户', async () => {
    localStorage.setItem('mall-pc-tenant-context', JSON.stringify({
      browseTenantId: '1', accountTenantId: '2009', accountSessionKind: 'platform',
    }))
    const context = await import('./tenant-context')
    expect(context.getTenantContext()).toMatchObject({ browseTenantId: '1', accountTenantId: null })
    expect(context.getAccountRequestTenant()).toBe('1')
    expect(context.getAccountSessionCandidate()).toEqual({ tenantId: '2009', kind: 'platform' })
    const epoch = context.getTenantContext().epoch
    expect(context.confirmPlatformAccountContext({ tenantId: 2009, userId: 77 }, epoch)).toBe(true)
    expect(context.getTenantContext()).toMatchObject({ browseTenantId: '1', accountTenantId: '2009',
      accountUserId: '77', accountSessionKind: 'platform' })
    expect(context.getAccountRequestTenant()).toBe('2009')
  })
  it('账户页刷新忽略浏览租户参数与标签页入口缓存', async () => {
    vi.resetModules()
    const values = new Map<string, string>([['mall-scoped-entry', '2009']])
    const browser = new EventTarget() as EventTarget & { location: { href: string } }
    browser.location = { href: 'https://mall.local/member/orders?tenantId=2009' }
    vi.stubGlobal('window', browser)
    vi.stubGlobal('sessionStorage', { getItem: (key: string) => values.get(key) ?? null, setItem: (key: string, value: string) => values.set(key, value) })
    const context = await import('./tenant-context')
    expect(context.entryTenantId).toBe('1')
    expect(context.invalidEntryHint).toBe(false)
  })
  it('目录关闭或请求失败时保留旧入口，失败不假造目录', async () => {
    const context = await import('./tenant-context')
    context.acceptTenantCatalog({ enabled: false, legacyTenantId: 1, publicTenantId: null, tenants: [], capabilities: { browseSwitch: false, registrationSelection: false } })
    expect(context.getTenantContext().catalogStatus).toBe('legacy')
    expect(context.getRegistrationEntryPath()).toBe('/register')
    context.tenantCatalogUnavailable()
    expect(context.getTenantContext()).toMatchObject({ catalogStatus: 'unavailable', catalog: null, browseTenantId: '1' })
    context.tenantCatalogUnavailable(true)
    expect(context.getTenantContext().catalogStatus).toBe('legacy')
  })
  it('带其他租户或重复租户参数的URL被拒绝，不静默读成原租户商品', async () => {
    const { acceptsLegacyTenantLink } = await import('./tenant-context')
    expect(acceptsLegacyTenantLink({})).toBe(true)
    expect(acceptsLegacyTenantLink({ tenantId: '1', id: '20' })).toBe(true)
    expect(acceptsLegacyTenantLink({ tenantId: '88' })).toBe(false)
    expect(acceptsLegacyTenantLink({ 'tenant-id': '88' })).toBe(false)
    expect(acceptsLegacyTenantLink({ tenantId: ['1', '88'] })).toBe(false)
  })
})
