<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { fulfillmentApi } from '@/api/fulfillment'
import { useAccountOperation } from '@/composables/useAccountOperation'
import { addressLocationText, hasAddressLocation, mapAvailability } from '@/utils/fulfillment'
import type { AddressLocation, LocationCapability } from '@/types/fulfillment'

const props = defineProps<{ modelValue: boolean; location?: AddressLocation }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const operation = useAccountOperation()
const capability = ref<LocationCapability | null>(null)
const failed = ref(false)
const loading = ref(false)
let generation = 0
const availability = computed(() => mapAvailability(capability.value, failed.value))
async function load() {
  if (!props.modelValue || loading.value || !operation.current()) return
  const current = ++generation
  loading.value = true
  failed.value = false
  try {
    const result = await fulfillmentApi.getLocationCapability()
    if (current === generation && operation.current() && props.modelValue) capability.value = result
  } catch { if (current === generation && operation.current() && props.modelValue) failed.value = true }
  finally { if (current === generation && operation.current()) loading.value = false }
}
watch(() => props.modelValue, (open) => {
  generation += 1; loading.value = false; capability.value = null; failed.value = false
  if (open) void load()
})
</script>

<template>
  <el-dialog :model-value="modelValue" title="地址地图选点" width="540px" append-to-body @update:model-value="emit('update:modelValue', $event)">
    <div class="map-fallback" data-testid="map-fallback">
      <el-skeleton v-if="loading" :rows="2" animated />
      <template v-else><strong>{{ availability.message }}</strong><p>可关闭此窗口，继续填写省市区及街道门牌并保存。不会自动获取您的位置，也不会向地图服务发送收货人、电话或地址。</p><el-button v-if="failed" @click="load">重新检查地图服务</el-button></template>
    </div>
    <p>{{ addressLocationText(location) }}</p>
    <p v-if="hasAddressLocation(location)">已有点位：经度 {{ location?.longitude }}，纬度 {{ location?.latitude }}。仅为保存的收货点位，不是实时位置。</p>
    <template #footer><el-button type="primary" @click="emit('update:modelValue', false)">继续手工填写</el-button></template>
  </el-dialog>
</template>

<style scoped>.map-fallback { padding: 22px; border: 1px dashed var(--line); background: #f5f8f9; border-radius: 6px; }.map-fallback strong { font-size: 15px; }p { color: var(--muted); line-height: 1.8; font-size: 13px; }</style>
