import { ApiError, request } from './http'
import axios, { AxiosHeaders } from 'axios'
import { entryTenantId, invalidEntryHint, getTenantContext, isCurrentTenantEpoch, legacyTenantId } from '@/utils/tenant-context'
import { ensureEntryReady } from './http'
import type { AuthEntry } from '@/utils/auth-challenge.mjs'
import { parseRegistrationOptions } from '@/utils/registration-choice.mjs'
import { createTenantAuthClient } from '@/utils/tenant-auth-client.mjs'

// Independent transport: only the verified candidate token may reach /session.
// Never use the current account's interceptor or automatic refresh for authentication.
const authHttp = axios.create({ baseURL: `${import.meta.env.VITE_API_BASE_URL || ''}${import.meta.env.VITE_API_PREFIX || '/app-api'}`,
  timeout: 12000, withCredentials: false })
const targetAuth = createTenantAuthClient(async ({ path, headers, ...config }) => {
  try { return (await authHttp.request({ ...config, url: path, headers: new AxiosHeaders(headers) })).data }
  catch (error) {
    if (axios.isAxiosError(error)) throw new ApiError('商城认证服务暂不可用', error.response?.status)
    throw error
  }
}, legacyTenantId)

export async function getRegistrationOptions() {
  try { return parseRegistrationOptions(await request({ url: '/member/mall-auth/registration-options', scope: 'entry', headers: { 'x-silent-error': true } })) }
  catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.code === 404)) return parseRegistrationOptions({ enabled: false, tenants: [], crossTenantReady: false })
    throw error
  }
}

export async function getAuthEntry(target = entryTenantId): Promise<Readonly<AuthEntry>> {
  const epoch = getTenantContext().epoch
  if (invalidEntryHint || entryTenantId !== legacyTenantId) await ensureEntryReady()
  return targetAuth.discover(target, () => isCurrentTenantEpoch(epoch))
}
export async function challengeRequest<T>(endpoint: string, data: Record<string, unknown>, current = () => true): Promise<T> {
  const epoch = getTenantContext().epoch
  if (invalidEntryHint || entryTenantId !== legacyTenantId) await ensureEntryReady()
  return targetAuth.execute(endpoint, data, () => current() && isCurrentTenantEpoch(epoch)) as Promise<T>
}
