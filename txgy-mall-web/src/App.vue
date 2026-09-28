<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { loadTenantCatalog } from '@/api/tenant'

const authStore = useAuthStore()
const cartStore = useCartStore()
let lastRefreshAt = 0

const refreshWhenActive = () => {
  if (document.visibilityState !== 'visible' || !authStore.isLoggedIn) return
  const now = Date.now()
  if (now - lastRefreshAt < 3000) return
  lastRefreshAt = now
  void Promise.allSettled([authStore.loadProfile(), cartStore.fetchCart()])
}

onMounted(() => {
  void loadTenantCatalog()
  void authStore.restoreSession()
  document.addEventListener('visibilitychange', refreshWhenActive)
  window.addEventListener('focus', refreshWhenActive)
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', refreshWhenActive)
  window.removeEventListener('focus', refreshWhenActive)
})
</script>

<template>
  <div v-if="authStore.sessionError" role="alert" class="session-recovery">
    {{ authStore.sessionError }}
    <button type="button" @click="authStore.restoreSession()">重新核验</button>
    <button type="button" @click="authStore.logout().catch(() => {})">退出旧会话</button>
  </div>
  <RouterView />
</template>

<style scoped>
.session-recovery { padding: 12px 24px; background: #fff7e6; color: #7a4b00; }
.session-recovery button { margin-left: 12px; padding: 4px 12px; cursor: pointer; }
</style>
