import axios, { AxiosError, AxiosHeaders, type AxiosRequestConfig, type RawAxiosHeaders } from 'axios'
import { ElMessage } from 'element-plus'
import { acceptTenantCatalog, assertResponseTenant, confirmAccountContext, entryTenantId, invalidEntryHint, legacyTenantId, getAccountRequestTenant, getTenantContext, invalidateAccountContext, isCurrentTenantEpoch, type MallTenantConfig } from '@/utils/tenant-context'
import { scopedTokenKey } from '@/utils/tenant-entry.mjs'
import { createTenantAuthClient, SessionRestoreError } from '@/utils/tenant-auth-client.mjs'

interface ApiEnvelope<T> { code: number; data: T; msg?: string }
export type RequestScope = 'account' | 'browse' | 'auth' | 'entry'
export interface MallRequestConfig extends AxiosRequestConfig { scope?: RequestScope }
export class ApiError extends Error {
  constructor(message: string, public status?: number, public code?: number) { super(message) }
}
export class StaleSessionError extends Error {
  constructor() { super('账号上下文已变化，请重新操作') }
}
const ACCESS_TOKEN_KEY = scopedTokenKey('mall-pc-access-token', entryTenantId, legacyTenantId)
const REFRESH_TOKEN_KEY = scopedTokenKey('mall-pc-refresh-token', entryTenantId, legacyTenantId)
const API_BASE = `${import.meta.env.VITE_API_BASE_URL || ''}${import.meta.env.VITE_API_PREFIX || '/app-api'}`
const emitToken = () => window.dispatchEvent(new Event('mall:token-updated'))
function writeTokens(accessToken: string, refreshToken: string) {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
  emitToken()
}
export const tokenStorage = {
  getAccess: () => localStorage.getItem(ACCESS_TOKEN_KEY) || '',
  getRefresh: () => localStorage.getItem(REFRESH_TOKEN_KEY) || '',
  set(accessToken: string, refreshToken: string) {
    invalidateAccountContext()
    writeTokens(accessToken, refreshToken)
  },
  clear() {
    invalidateAccountContext()
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    emitToken()
  },
}
window.addEventListener('storage', (event: StorageEvent) => {
  if (event.key === null || event.key === ACCESS_TOKEN_KEY || event.key === REFRESH_TOKEN_KEY) {
    invalidateAccountContext()
    emitToken()
  }
})

interface RequestContext { tenant: string; userId: string | null; epoch: number; accessToken: string; refreshToken: string; scope: RequestScope }
function capture(scope: RequestScope): RequestContext {
  const context = getTenantContext()
  const tenant = scope === 'account' ? getAccountRequestTenant() : context.browseTenantId
  const mayAuthenticate = scope === 'account' || (scope === 'browse' && tenant === context.accountTenantId)
  return { tenant, userId: context.accountUserId, epoch: context.epoch, scope, accessToken: mayAuthenticate ? tokenStorage.getAccess() : '',
    refreshToken: mayAuthenticate ? tokenStorage.getRefresh() : '' }
}
function assertCurrent(context: RequestContext) {
  if (!isCurrentTenantEpoch(context.epoch) || (context.scope === 'account' && context.tenant !== getAccountRequestTenant())) throw new StaleSessionError()
}
function ownsTokens(context: RequestContext) {
  return isCurrentTenantEpoch(context.epoch) && context.accessToken === tokenStorage.getAccess()
    && context.refreshToken === tokenStorage.getRefresh() && context.tenant === getAccountRequestTenant()
}
const authorizationValue = (token: string) => token.startsWith('Bearer ') ? token : `Bearer ${token}`
const refreshing = new Map<string, Promise<string>>()

async function refreshAccessToken(context: RequestContext): Promise<string> {
  assertCurrent(context)
  if (!context.refreshToken) throw new Error('登录状态已失效')
  // A late 401 from this same session can reuse its already-rotated access token.
  if (context.accessToken !== tokenStorage.getAccess()) return tokenStorage.getAccess()
  const key = JSON.stringify([context.tenant, context.epoch, context.accessToken, context.refreshToken])
  const pending = refreshing.get(key)
  if (pending) return pending
  const promise = axios.post<ApiEnvelope<{ accessToken: string; refreshToken: string; tenantId?: string | number }>>(
    `${API_BASE}/member/auth/refresh-token`, undefined,
    { params: { refreshToken: context.refreshToken }, headers: { 'tenant-id': context.tenant, terminal: '20' }, timeout: 12000 },
  ).then(async ({ data }) => {
    if (!ownsTokens(context)) throw new StaleSessionError()
    if (data.code !== 0 || !data.data?.accessToken || !data.data.refreshToken) throw new ApiError(data.msg || '登录状态已失效', undefined, data.code)
    assertResponseTenant(data.data.tenantId, context.tenant)
    if (context.userId) {
      const verifier = createTenantAuthClient(async ({ path, headers, ...config }) =>
        (await client.request({ ...config, url: path, headers: new AxiosHeaders(headers) })).data)
      await verifier.verifySession(data.data, context.tenant, () => ownsTokens(context), context.userId)
    }
    if (!ownsTokens(context)) throw new StaleSessionError()
    writeTokens(data.data.accessToken, data.data.refreshToken)
    return data.data.accessToken
  }).finally(() => { refreshing.delete(key) })
  refreshing.set(key, promise)
  return promise
}

function clearOwnedSession(context: RequestContext) {
  if (!ownsTokens(context)) return
  tokenStorage.clear()
  window.dispatchEvent(new Event('mall:unauthorized'))
}
const client = axios.create({ baseURL: API_BASE, timeout: 12000,
  headers: { Accept: 'application/json', 'Content-Type': 'application/json;charset=UTF-8' } })

let entryCheck: Promise<void> | undefined
export async function ensureEntryReady(forceRefresh = false): Promise<void> {
  if (invalidEntryHint) throw new Error('商城入口参数无效，请返回原商城')
  if (entryTenantId === legacyTenantId) return
  // The tenant directory is entry metadata, not per-request data. Re-fetching it
  // for every browse request emits a context update and can make detail pages
  // recursively reload themselves.
  const status = getTenantContext().catalogStatus
  if (!forceRefresh && (status === 'ready' || status === 'legacy')) return
  if (!forceRefresh && status === 'unavailable') throw new Error('商城入口暂无法核验，请稍后重试')
  if (entryCheck) return entryCheck
  const pending = (async () => {
    const response = await client.request<ApiEnvelope<MallTenantConfig>>({ url: '/member/mall-tenant/config', method: 'GET', headers: new AxiosHeaders() })
    if (response.data?.code !== 0) throw new Error('商城入口暂无法核验，请稍后重试')
    acceptTenantCatalog(response.data.data)
  })()
  entryCheck = pending
  try { await pending } finally { if (entryCheck === pending) entryCheck = undefined }
}

let restoring: { key: string; promise: Promise<void> } | undefined
export async function ensureAccountSession(): Promise<void> {
  const context = capture('account')
  if (invalidEntryHint || entryTenantId !== legacyTenantId) await ensureEntryReady()
  assertCurrent(context)
  if (!context.accessToken || getTenantContext().accountTenantId) return
  const key = JSON.stringify([context.epoch, context.accessToken, context.refreshToken])
  if (restoring?.key === key) return restoring.promise
  const promise = (async () => {
    const verifier = createTenantAuthClient(async ({ path, headers, ...config }) =>
      (await client.request({ ...config, url: path, headers: new AxiosHeaders(headers) })).data, legacyTenantId)
    try {
      const identity = await verifier.restoreSession({ tenantId: context.tenant,
        accessToken: context.accessToken, refreshToken: context.refreshToken }, () => ownsTokens(context))
      if (!ownsTokens(context)) throw new StaleSessionError()
      confirmAccountContext(identity, context.epoch)
      if (identity.accessToken !== context.accessToken || identity.refreshToken !== context.refreshToken) {
        writeTokens(identity.accessToken, identity.refreshToken)
      }
    } catch (error) {
      if (!ownsTokens(context) || (error instanceof SessionRestoreError && error.kind === 'stale')) throw new StaleSessionError()
      if (error instanceof SessionRestoreError && error.kind === 'invalid') clearOwnedSession(context)
      throw error
    }
  })()
  restoring = { key, promise }
  try { await promise } finally { if (restoring?.promise === promise) restoring = undefined }
}

async function execute<T>(config: MallRequestConfig, context: RequestContext, retried = false): Promise<T> {
  assertCurrent(context)
  if (context.scope === 'auth' && entryTenantId !== legacyTenantId) throw new Error('此商城请使用租户绑定的认证入口，不能回退旧版认证')
  const { scope: _scope, ...transport } = config
  const headers = new AxiosHeaders(config.headers as RawAxiosHeaders)
  headers.delete('Authorization')
  headers.delete('tenant-id')
  if (context.scope !== 'entry') headers.set('tenant-id', context.tenant)
  headers.set('terminal', '20')
  if (context.accessToken) headers.set('Authorization', authorizationValue(context.accessToken))
  let envelope: ApiEnvelope<T>
  try {
    if (context.scope !== 'entry' && (invalidEntryHint || entryTenantId !== legacyTenantId)) {
      const method = String(config.method || 'GET').toUpperCase()
      await ensureEntryReady(context.scope === 'account' && !['GET', 'HEAD', 'OPTIONS'].includes(method))
      assertCurrent(context)
    }
    const response = await client.request<ApiEnvelope<T>>({ ...transport, headers })
    assertCurrent(context)
    if (config.responseType === 'blob' && response.data instanceof Blob) {
      if (!response.data.type.includes('json')) return response.data as T
      envelope = JSON.parse(await response.data.text()) as ApiEnvelope<T>
      assertCurrent(context)
    } else envelope = response.data
  } catch (reason) {
    assertCurrent(context)
    if (reason instanceof AxiosError) {
      if (reason.response?.status === 401) envelope = { code: 401, data: undefined as T, msg: '登录状态已失效' }
      else throw new ApiError(reason.code === 'ECONNABORTED' ? '请求超时，请检查商城服务是否已启动' : reason.response?.data?.msg || reason.message || '网络请求失败', reason.response?.status)
    } else throw reason
  }
  if (!envelope || typeof envelope !== 'object' || typeof envelope.code !== 'number') throw new Error('服务器返回了无法识别的数据')
  if (envelope.code === 0) return envelope.data
  if (envelope.code === 401 && context.accessToken && !retried && context.scope !== 'auth' && context.scope !== 'entry') {
    let accessToken: string
    try { accessToken = await refreshAccessToken(context) }
    catch (reason) { clearOwnedSession(context); throw reason }
    assertCurrent(context)
    return execute<T>(config, { ...context, accessToken, refreshToken: tokenStorage.getRefresh() }, true)
  }
  throw new ApiError(envelope.msg || '请求失败', undefined, envelope.code)
}

export async function request<T>(config: MallRequestConfig): Promise<T> {
  // Capture before the first await, including authentication/anonymous requests.
  let context = capture(config.scope ?? 'account')
  try {
    if (context.scope === 'account' && context.accessToken && !getTenantContext().accountTenantId) {
      await ensureAccountSession()
      assertCurrent(context)
      context = capture(context.scope)
    }
    return await execute<T>(config, context)
  }
  catch (error) {
    if (!(error instanceof StaleSessionError) && config.headers?.['x-silent-error'] !== true) ElMessage.error(error instanceof Error ? error.message : '请求失败')
    throw error
  }
}
export function requestBlob(config: MallRequestConfig) { return request<Blob>({ ...config, responseType: 'blob' }) }
