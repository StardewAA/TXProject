import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi, memberApi } from '@/api/mall'
import { ApiError, StaleSessionError, tokenStorage, ensureAccountSession } from '@/api/http'
import { challengeRequest } from '@/api/mall-auth'
import { AccountContextError, assertResponseTenant, confirmAccountContext, getTenantContext, invalidateAccountContext, isCurrentTenantEpoch } from '@/utils/tenant-context'
import type { TokenResult, UserProfile } from '@/types/mall'
import type { ImageRegistrationPayload } from '@/utils/image-registration.mjs'

export const useAuthStore = defineStore('mall-auth', () => {
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)
  const sessionError = ref('')
  const accessToken = ref(tokenStorage.getAccess())
  const isLoggedIn = computed(() => Boolean(accessToken.value))
  let profileEpoch = getTenantContext().epoch
  let attempt = 0

  function applyToken(result: TokenResult) {
    assertResponseTenant(result.tenantId)
    if (!result.accessToken || !result.refreshToken || !/^[1-9]\d*$/.test(String(result.userId))) throw new Error('服务器未返回有效会员身份')
    tokenStorage.set(result.accessToken, result.refreshToken)
    if (result.tenantId !== undefined) confirmAccountContext({ tenantId: result.tenantId, userId: result.userId }, getTenantContext().epoch)
    accessToken.value = result.accessToken
  }

  async function loadProfile() {
    const epoch = getTenantContext().epoch
    if (!tokenStorage.getAccess()) {
      profile.value = null
      return null
    }
    try {
      const result = await memberApi.getProfile()
      if (!isCurrentTenantEpoch(epoch)) return null
      // /member/user/get is tenant-filtered but its legacy VO may omit tenantId.
      // A private request has already proved this entry's session; still require the same member.
      if (result.tenantId !== undefined || !getTenantContext().accountTenantId) assertResponseTenant(result.tenantId)
      if (getTenantContext().accountUserId && String(result.id) !== getTenantContext().accountUserId) throw new AccountContextError('会员身份与会话不一致')
      if (!isCurrentTenantEpoch(epoch)) return null
      sessionError.value = ''
      profile.value = result
      return profile.value
    } catch (error) {
      if (isCurrentTenantEpoch(epoch)) sessionError.value = error instanceof Error ? error.message : '登录状态暂无法核验，请重试'
      if (isCurrentTenantEpoch(epoch) && (error instanceof AccountContextError
        || (error instanceof ApiError && [401, 403].some(code => error.status === code || error.code === code)))) {
        tokenStorage.clear()
        window.dispatchEvent(new Event('mall:unauthorized'))
      }
      return null
    }
  }

  async function login(data: { mobile: string; password: string; tenantId?: string }, challenge = false, current = () => true) {
    const currentAttempt = ++attempt
    const authEpoch = invalidateAccountContext()
    loading.value = true
    try {
      const result = challenge ? await challengeRequest<TokenResult>('login', data, () => currentAttempt === attempt && current()) : await authApi.login(data)
      if (currentAttempt !== attempt || !isCurrentTenantEpoch(authEpoch) || !current()) throw new StaleSessionError()
      if (challenge && String(result.tenantId) !== String(data.tenantId)) throw new AccountContextError('认证返回租户不一致')
      applyToken(result)
      await loadProfile()
      if (currentAttempt !== attempt) throw new StaleSessionError()
    } finally {
      if (currentAttempt === attempt) loading.value = false
    }
  }

  async function register(data: { mobile: string; password: string; code: string; nickname: string; agree: true; memberIdentity: 'buyer' | 'supplier'; tenantId?: string; challengeId?: string }, challenge = false, current = () => true) {
    const currentAttempt = ++attempt
    const authEpoch = invalidateAccountContext()
    loading.value = true
    try {
      const result = challenge ? await challengeRequest<TokenResult>('register', data, () => currentAttempt === attempt && current()) : await authApi.register(data)
      if (currentAttempt !== attempt || !isCurrentTenantEpoch(authEpoch) || !current()) throw new StaleSessionError()
      if (challenge && String(result.tenantId) !== String(data.tenantId)) throw new AccountContextError('认证返回租户不一致')
      applyToken(result)
      await loadProfile()
      if (currentAttempt !== attempt) throw new StaleSessionError()
    } finally {
      if (currentAttempt === attempt) loading.value = false
    }
  }

  async function imageAuthenticate(operation: 'account-login' | 'register-image', data: Record<string, unknown> & { tenantId: string }, current: () => boolean) {
    const currentAttempt = ++attempt
    const authEpoch = invalidateAccountContext()
    loading.value = true
    try {
      const result = await challengeRequest<TokenResult>(operation, data, () => currentAttempt === attempt && current())
      if (currentAttempt !== attempt || !isCurrentTenantEpoch(authEpoch) || !current()) throw new StaleSessionError()
      if (String(result.tenantId) !== data.tenantId) throw new AccountContextError('认证返回租户不一致')
      applyToken(result)
      await loadProfile()
      if (currentAttempt !== attempt) throw new StaleSessionError()
    } finally {
      if (currentAttempt === attempt) loading.value = false
    }
  }

  function accountLogin(data: { tenantId: string; account: string; password: string }, current = () => true) {
    return imageAuthenticate('account-login', data, current)
  }

  function registerImage(data: ImageRegistrationPayload & { password: string; confirmPassword: string; contactMobile?: string; nickname?: string; agree: true; memberIdentity: 'buyer' | 'supplier' }, current = () => true) {
    return imageAuthenticate('register-image', { ...data }, current)
  }

  async function logout() {
    // Capture the original token, then invalidate immediately; a late logout cannot clear a new login.
    const pending = isLoggedIn.value ? authApi.logout() : Promise.resolve()
    attempt += 1
    tokenStorage.clear()
    accessToken.value = ''
    profile.value = null
    loading.value = false
    try { await pending } catch (error) { if (!(error instanceof StaleSessionError)) throw error }
  }

  async function restoreSession() {
    accessToken.value = tokenStorage.getAccess()
    if (!tokenStorage.getAccess()) return
    const epoch = getTenantContext().epoch
    try { await ensureAccountSession(); if (isCurrentTenantEpoch(epoch)) await loadProfile() }
    catch (error) {
      if (isCurrentTenantEpoch(epoch)) sessionError.value = error instanceof Error ? error.message : '登录状态暂无法核验，请重试'
    }
  }

  window.addEventListener('mall:unauthorized', () => {
    accessToken.value = ''
    profile.value = null
    sessionError.value = ''
  })

  window.addEventListener('mall:token-updated', () => {
    accessToken.value = tokenStorage.getAccess()
    sessionError.value = ''
    if (profileEpoch !== getTenantContext().epoch) { profileEpoch = getTenantContext().epoch; profile.value = null }
  })

  return { profile, loading, sessionError, isLoggedIn, login, register, accountLogin, registerImage, logout, loadProfile, restoreSession }
})
