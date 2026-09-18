<template>
  <!-- #ifdef H5 -->
  <view v-if="directory.enabled || foreign" class="tenant-entry">
    <picker v-if="!target" :range="names" :value="index" :disabled="busy" @change="index = Number($event.detail.value)">
      <view>当前商城：{{ currentName }} · 选择：{{ names[index] }} ▾</view>
    </picker>
    <button size="mini" :disabled="busy" @tap="enter">{{ target ? '进入所选商城注册' : '进入商城' }}</button>
    <button v-if="foreign" size="mini" @tap="original">返回原商城</button>
    <view v-if="target">切换后重新填写注册信息，原商城账号保留。</view>
    <view v-if="error" role="alert">{{ error }}</view>
  </view>
  <!-- #endif -->
</template>
<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { tenantId } from '@/sheep/config';
import { tenantTransport, getControlledTenantId } from '@/sheep/helper/tenant-runtime';
import { ENTRY_KEY, parseEntryDirectory, selectEntry } from '@/sheep/helper/tenant-entry.mjs';
const props = defineProps({ target: String, purpose: { type: String, default: 'home' } });
const directory = ref({ enabled: false, tenants: [] }), busy = ref(false), error = ref(''), index = ref(0);
const current = getControlledTenantId(), foreign = current !== String(tenantId);
const entries = computed(() => directory.value.tenants.some(t => t.tenantId === String(tenantId)) ? directory.value.tenants
  : [{ tenantId: String(tenantId), name: '原商城' }, ...directory.value.tenants]);
const names = computed(() => entries.value.map(t => t.name));
const currentName = computed(() => entries.value.find(t => t.tenantId === current)?.name || '当前商城');
let alive = true;
onBeforeUnmount(() => { alive = false; });
async function catalog() { const response = await tenantTransport('/member/mall-tenant/config'); if (response?.code !== 0) throw new Error('商城目录暂不可用'); return response.data; }
onMounted(async () => {
  // #ifdef H5
  try { const value = await catalog(); if (alive) { directory.value = parseEntryDirectory(value, String(tenantId)); index.value = Math.max(0, entries.value.findIndex(t => t.tenantId === current)); } } catch { /* Do not invent enabled entries. */ }
  // #endif
});
async function enter() {
  // #ifdef H5
  if (busy.value) return;
  busy.value = true; error.value = '';
  const target = props.target || entries.value[index.value]?.tenantId;
  try {
    const value = await catalog();
    if (!alive || target !== (props.target || entries.value[index.value]?.tenantId)) return;
    window.location.assign(selectEntry(sessionStorage, value, target, String(tenantId), 'h5', props.purpose));
  } catch (reason) { if (alive) error.value = reason.message || '切换失败，请重试'; }
  finally { busy.value = false; }
  // #endif
}
function original() {
  // #ifdef H5
  sessionStorage.removeItem(ENTRY_KEY); window.location.assign('/pages/index/index');
  // #endif
}
</script>
<style scoped>.tenant-entry{margin:16rpx;padding:18rpx;background:#eef6f4;border-radius:12rpx;font-size:24rpx;color:#176957;line-height:1.8}.tenant-entry button{margin:12rpx 12rpx 0 0}</style>
