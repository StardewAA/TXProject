<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { tenantApi } from '@/api/tenant'
import { entryTenantId, legacyTenantId, invalidateAccountContext } from '@/utils/tenant-context'
import { parseEntryDirectory, selectEntry, type EntryDirectory } from '@/utils/tenant-entry.mjs'

const props = defineProps<{ target?: string; purpose?: 'home' | 'register' }>()
const directory = ref<EntryDirectory>({ enabled: false, tenants: [] })
const busy = ref(false)
const error = ref('')
const selected = ref(entryTenantId)
let alive = true

onBeforeUnmount(() => { alive = false })
onMounted(async () => {
  try {
    const value = await tenantApi.config()
    if (alive) directory.value = parseEntryDirectory(value, legacyTenantId)
  } catch { /* Do not infer entry capability. */ }
})

async function enter() {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const target = props.target || selected.value
    const value = await tenantApi.config()
    if (!alive || target !== (props.target || selected.value)) return
    const path = selectEntry(sessionStorage, value, target, legacyTenantId, 'pc', props.purpose || 'home')
    invalidateAccountContext()
    window.location.assign(path)
  } catch (reason) {
    if (alive) error.value = reason instanceof Error ? reason.message : '切换失败，请重试'
  } finally {
    if (alive) busy.value = false
  }
}
</script>

<template>
  <span v-if="directory.enabled || entryTenantId !== legacyTenantId" class="tenant-switch">
    <template v-if="!target">
      <span class="tenant-switch__label"><i />商城</span>
      <span class="tenant-switch__select-wrap">
        <select v-model="selected" aria-label="选择商城">
          <option v-if="!directory.tenants.some(t => t.tenantId === legacyTenantId)" :value="legacyTenantId">公共商城</option>
          <option v-for="tenant in directory.tenants" :key="tenant.tenantId" :value="tenant.tenantId">{{ tenant.name }}</option>
        </select>
      </span>
    </template>
    <button :disabled="busy || (!target && selected === entryTenantId)" @click="enter">
      {{ busy ? '切换中…' : target ? '前往注册' : selected === entryTenantId ? '当前商城' : '切换' }}
    </button>
    <small v-if="target">将在所选商城完成注册</small>
    <small v-if="error" class="tenant-switch__error" role="alert">{{ error }}</small>
  </span>
</template>

<style scoped>
.tenant-switch{display:inline-flex;align-items:center;gap:7px;min-width:0;color:#d9e6e2}
.tenant-switch__label{display:inline-flex;align-items:center;gap:6px;max-width:150px;color:#fff;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.tenant-switch__label i{width:7px;height:7px;flex:0 0 auto;border-radius:50%;background:#40c39f;box-shadow:0 0 0 3px rgba(64,195,159,.14)}
.tenant-switch__select-wrap{position:relative;display:inline-flex;align-items:center}
.tenant-switch__select-wrap::after{content:'⌄';position:absolute;right:8px;top:3px;color:#c8dbd5;pointer-events:none;font-size:14px}
select{width:156px;height:26px;padding:0 25px 0 9px;border:1px solid rgba(255,255,255,.22);border-radius:6px;outline:none;appearance:none;background:#303d44;color:#eef7f4;font-size:12px;cursor:pointer}
select:hover,select:focus{border-color:#65b9a7;background:#35474c}
select option{background:#fff;color:#263540}
button{height:26px;padding:0 10px;border:1px solid #57aa98;border-radius:6px;background:transparent;color:#c5eee5;font-size:12px;white-space:nowrap}
button:not(:disabled):hover{background:#147d6b;border-color:#147d6b;color:#fff}
button:disabled{border-color:rgba(255,255,255,.12);color:#8c999d;cursor:default}
small{color:#aebfc0;white-space:nowrap}.tenant-switch__error{color:#ffb4a3}
</style>
