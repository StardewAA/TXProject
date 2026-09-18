<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { tenantApi } from '@/api/tenant'
import { entryTenantId, legacyTenantId, invalidateAccountContext } from '@/utils/tenant-context'
import { parseEntryDirectory, selectEntry, type EntryDirectory } from '@/utils/tenant-entry.mjs'
const props = defineProps<{ target?: string; purpose?: 'home' | 'register' }>()
const directory = ref<EntryDirectory>({ enabled: false, tenants: [] }), busy = ref(false), error = ref('')
const selected = ref(entryTenantId)
let alive = true
onBeforeUnmount(() => { alive = false })
onMounted(async () => { try { const value = await tenantApi.config(); if (alive) directory.value = parseEntryDirectory(value, legacyTenantId) } catch { /* Do not infer entry capability. */ } })
async function enter() {
  if (busy.value) return
  busy.value = true; error.value = ''
  try {
    const target = props.target || selected.value
    const value = await tenantApi.config()
    if (!alive || target !== (props.target || selected.value)) return
    const path = selectEntry(sessionStorage, value, target, legacyTenantId, 'pc', props.purpose || 'home')
    invalidateAccountContext(); window.location.assign(path)
  } catch (reason) { if (alive) error.value = reason instanceof Error ? reason.message : '切换失败，请重试' }
  finally { if (alive) busy.value = false }
}
</script>
<template><span v-if="directory.enabled || entryTenantId !== legacyTenantId" class="tenant-switch">
  <template v-if="!target"><span>当前商城：</span><select v-model="selected" aria-label="主商城选择"><option v-if="!directory.tenants.some(t => t.tenantId === legacyTenantId)" :value="legacyTenantId">原商城</option><option v-for="tenant in directory.tenants" :key="tenant.tenantId" :value="tenant.tenantId">{{ tenant.name }}</option></select></template>
  <button :disabled="busy" @click="enter">{{ target ? '进入所选商城注册' : '进入商城' }}</button>
  <small v-if="target">切换后请重新填写注册信息；原商城账号保留。</small><small v-if="error" role="alert">{{ error }}</small>
</span></template>
<style scoped>.tenant-switch{display:inline-flex;gap:8px;align-items:center;flex-wrap:wrap}select,button{padding:6px;border:1px solid #a7c9c1;border-radius:4px;background:#fff;color:#135d50}small{display:block;width:100%}</style>
