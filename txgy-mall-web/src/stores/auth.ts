import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi, memberApi } from '@/api/mall'
import { tokenStorage } from '@/api/http'
import type { TokenResult, UserProfile } from '@/types/mall'

export const useAuthStore = defineStore('mall-auth', () => {
  const profile = ref<UserProfile | null>(null)
  const loading = ref(false)
  const accessToken = ref(tokenStorage.getAccess())
  const isLoggedIn = computed(() => Boolean(accessToken.value))

  function applyToken(result: TokenResult) {
    tokenStorage.set(result.accessToken, result.refreshToken)
    accessToken.value = result.accessToken
  }

  async function loadProfile() {
    if (!tokenStorage.getAccess()) {
      profile.value = null
      return null
    }
    try {
      profile.value = await memberApi.getProfile()
      return profile.value
    } catch {
      return null
    }
  }

  async function login(data: { mobile: string; password: string }) {
    loading.value = true
    try {
      applyToken(await authApi.login(data))
      await loadProfile()
    } finally {
      loading.value = false
    }
  }

  async function register(data: { mobile: string; password: string; code: string; nickname: string; agree: true }) {
    loading.value = true
    try {
      applyToken(await authApi.register(data))
      await loadProfile()
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      if (isLoggedIn.value) await authApi.logout()
    } finally {
      tokenStorage.clear()
      accessToken.value = ''
      profile.value = null
    }
  }

  async function restoreSession() {
    accessToken.value = tokenStorage.getAccess()
    if (tokenStorage.getAccess()) await loadProfile()
  }

  window.addEventListener('mall:unauthorized', () => {
    accessToken.value = ''
    profile.value = null
  })

  window.addEventListener('mall:token-updated', () => {
    accessToken.value = tokenStorage.getAccess()
  })

  return { profile, loading, isLoggedIn, login, register, logout, loadProfile, restoreSession }
})
