<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { RouterView } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'

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
  <RouterView />
</template>
