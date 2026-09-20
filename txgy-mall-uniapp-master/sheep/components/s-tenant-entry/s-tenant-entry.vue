<template>
  <!-- #ifdef H5 -->
  <view v-if="directory.enabled || foreign" class="tenant-entry">
    <view class="tenant-entry__mark">企</view>
    <view class="tenant-entry__content">
      <text class="tenant-entry__eyebrow">当前商城</text>
      <text class="tenant-entry__name">{{ currentName }}</text>
      <picker v-if="!target" :range="names" :value="index" :disabled="busy" @change="index = Number($event.detail.value)">
        <view class="tenant-entry__picker">{{ selectedCurrent ? '选择其他商城' : `切换至 ${names[index]}` }} <text>⌄</text></view>
      </picker>
      <view v-if="target" class="tenant-entry__hint">将在所选商城完成注册</view>
    </view>
    <view class="tenant-entry__actions">
      <button class="tenant-entry__primary" size="mini" :disabled="busy || (!target && selectedCurrent)" @tap="enter">
        {{ busy ? '切换中' : target ? '前往注册' : selectedCurrent ? '当前商城' : '进入' }}
      </button>
      <button v-if="foreign" class="tenant-entry__plain" size="mini" @tap="original">公共商城</button>
    </view>
    <view v-if="error" class="tenant-entry__error" role="alert">{{ error }}</view>
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
  : [{ tenantId: String(tenantId), name: '公共商城' }, ...directory.value.tenants]);
const names = computed(() => entries.value.map(t => t.name));
const currentName = computed(() => entries.value.find(t => t.tenantId === current)?.name || '当前商城');
const selectedCurrent = computed(() => entries.value[index.value]?.tenantId === current);
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

<style scoped>
.tenant-entry{position:relative;display:flex;align-items:center;gap:18rpx;margin:18rpx 24rpx;padding:18rpx 20rpx;border:1rpx solid #d9ebe5;border-radius:20rpx;background:linear-gradient(135deg,#f8fcfa 0%,#edf7f3 100%);box-shadow:0 8rpx 22rpx rgba(17,89,78,.06);color:#183b35}
.tenant-entry__mark{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;flex:0 0 auto;border-radius:18rpx;background:linear-gradient(145deg,#158875,#0c695b);box-shadow:0 8rpx 16rpx rgba(20,125,107,.2);color:#fff;font-size:27rpx;font-weight:700}
.tenant-entry__content{min-width:0;flex:1;display:flex;flex-direction:column;gap:3rpx}
.tenant-entry__eyebrow{color:#7b908a;font-size:19rpx;line-height:1.35;letter-spacing:1rpx}
.tenant-entry__name{display:block;overflow:hidden;color:#183b35;font-size:27rpx;font-weight:700;line-height:1.45;white-space:nowrap;text-overflow:ellipsis}
.tenant-entry__picker{display:inline-flex;align-items:center;gap:5rpx;margin-top:4rpx;color:#147d6b;font-size:22rpx;line-height:1.45}
.tenant-entry__picker text{font-size:18rpx}.tenant-entry__hint{color:#71847f;font-size:21rpx}
.tenant-entry__actions{display:flex;flex-direction:column;gap:8rpx;flex:0 0 auto}
.tenant-entry__actions button{height:52rpx;margin:0;padding:0 18rpx;border:0;border-radius:26rpx;font-size:21rpx;line-height:52rpx}
.tenant-entry__primary{background:#147d6b!important;color:#fff!important}.tenant-entry__primary[disabled]{background:#dbe8e4!important;color:#80908c!important}
.tenant-entry__plain{background:#fff!important;color:#147d6b!important;box-shadow:inset 0 0 0 1rpx #bfd8d1}
.tenant-entry__error{position:absolute;left:20rpx;right:20rpx;top:100%;z-index:2;padding:8rpx 12rpx;border-radius:0 0 10rpx 10rpx;background:#fff0ed;color:#b43725;font-size:20rpx}
</style>
