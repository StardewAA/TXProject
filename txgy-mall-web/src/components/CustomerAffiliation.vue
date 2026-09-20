<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, watch } from 'vue'
import { request } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { getTenantContext } from '@/utils/tenant-context'
import { createCustomerAffiliationLoader, customerAffiliationMessage, type CustomerAffiliationState } from '@/utils/customer-affiliation.mjs'

const auth = useAuthStore()
const state = reactive<CustomerAffiliationState>({ result: null, loading: false, error: false, scope: '' })
let disposed = false
const scope = () => {
  const context = getTenantContext()
  return !disposed && auth.isLoggedIn && auth.profile?.id && context.accountUserId === String(auth.profile.id)
    ? `${context.epoch}:${context.accountTenantId}:${context.accountUserId}` : ''
}
const loader = createCustomerAffiliationLoader(state, scope, () => request({
  url: '/member/mall-tenant/customer-affiliation', scope: 'account', headers: { 'x-silent-error': true },
}))
const visible = computed(() => Boolean(scope()) && state.scope === scope() && state.result?.state !== 'DISABLED')
const retryable = computed(() => state.error || state.result?.state === 'TEMPORARILY_UNAVAILABLE' || state.result?.state === 'OWNER_UNAVAILABLE')
function reload() { void loader.refresh() }
watch(() => [auth.isLoggedIn, auth.profile?.id], reload, { immediate: true, flush: 'sync' })
window.addEventListener('mall:tenant-context-updated', reload)
onBeforeUnmount(() => { disposed = true; loader.reset(); window.removeEventListener('mall:tenant-context-updated', reload) })
</script>

<template>
  <section v-if="visible" class="customer-affiliation" aria-label="客户服务归属" aria-live="polite">
    <div><h3>客户服务归属</h3><p>{{ customerAffiliationMessage(state) }}</p>
      <small>仅说明为您提供服务的企业，不代表企业认证、员工身份或采购／销售权限。</small></div>
    <el-button v-if="retryable" :loading="state.loading" @click="reload">重新查询</el-button>
  </section>
</template>

<style scoped>
.customer-affiliation { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 20px 24px; margin: 20px 0; background: #fff; border: 1px solid #dce8e5; border-radius: 12px; }
h3 { font-size: 16px; margin: 0 0 10px; color: #173d37; }
p { margin: 0 0 8px; overflow-wrap: anywhere; }
small { color: #64748b; line-height: 1.7; }
</style>
