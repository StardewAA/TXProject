<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { fulfillmentApi } from '@/api/fulfillment'
import { useAccountOperation } from '@/composables/useAccountOperation'
import { addressLocationText, hasAddressLocation, mapAvailability } from '@/utils/fulfillment'
import { isAmapConfigured, loadAmap, type AmapApi, type AmapGeocoder, type AmapMap, type AmapMarker } from '@/utils/amap'
import type { AddressLocation, LocationCapability } from '@/types/fulfillment'

export interface AddressMapSelection extends AddressLocation {
  formattedAddress?: string
  areaId?: number
  areaName?: string
}

const props = defineProps<{ modelValue: boolean; location?: AddressLocation; addressText?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; picked: [value: AddressMapSelection] }>()
const operation = useAccountOperation()
const capability = ref<LocationCapability | null>(null)
const failed = ref(false)
const loading = ref(false)
const searchText = ref('')
const searching = ref(false)
const error = ref('')
const candidate = ref<AddressMapSelection | null>(null)
const containerId = `amap-picker-${Math.random().toString(36).slice(2)}`
const dialogOpened = ref(false)
let generation = 0
let amap: AmapApi | undefined
let map: AmapMap | undefined
let marker: AmapMarker | undefined
let geocoder: AmapGeocoder | undefined
const availability = computed(() => mapAvailability(capability.value, failed.value))
const ready = computed(() => availability.value.status === 'READY' && isAmapConfigured())

function destroyMap() { map?.destroy(); map = undefined; marker = undefined; geocoder = undefined; amap = undefined }
function coordinate(position: { getLng(): number; getLat(): number }) {
  return [Number(position.getLng().toFixed(6)), Number(position.getLat().toFixed(6))] as const
}
function cityName(value?: string | string[]) { return Array.isArray(value) ? value[0] || '' : value || '' }
function choose(lng: number, lat: number, fallbackAddress = '') {
  if (!amap || !map || !geocoder) return
  const point: [number, number] = [lng, lat]
  if (!marker) { marker = new amap.Marker({ position: point }); map.add(marker) } else marker.setPosition(point)
  map.setCenter(point)
  candidate.value = { longitude: lng, latitude: lat, coordSystem: 'GCJ02', locationSource: 'MAP_PICKER', locationPrecision: 'ADDRESS', formattedAddress: fallbackAddress }
  geocoder.getAddress(point, (status, result) => {
    if (status !== 'complete' || !candidate.value) return
    const regeo = result.regeocode
    const component = regeo?.addressComponent
    candidate.value = { ...candidate.value, formattedAddress: regeo?.formattedAddress || fallbackAddress,
      areaId: /^\d{6}$/.test(component?.adcode || '') ? Number(component?.adcode) : undefined,
      areaName: [component?.province, cityName(component?.city), component?.district].filter(Boolean).join(' ') }
  })
}
async function initMap(current: number) {
  if (!ready.value || !dialogOpened.value || map) return
  try {
    amap = await loadAmap()
    if (current !== generation || !props.modelValue || !dialogOpened.value || !operation.current()) return
    await nextTick()
    const element = document.getElementById(containerId)
    if (!element) throw new Error('地图容器不可用')
    const center: [number, number] = hasAddressLocation(props.location)
      ? [Number(props.location!.longitude), Number(props.location!.latitude)] : [108.93977, 34.341574]
    map = new amap.Map(element, { zoom: hasAddressLocation(props.location) ? 16 : 11, center, viewMode: '2D' })
    geocoder = new amap.Geocoder({ radius: 1000 })
    map.on('click', event => { const [lng, lat] = coordinate(event.lnglat); choose(lng, lat) })
    if (hasAddressLocation(props.location)) choose(center[0], center[1])
    searchText.value = props.addressText || ''
  } catch (cause) { if (current === generation) error.value = cause instanceof Error ? cause.message : '地图加载失败' }
}
async function load() {
  if (!props.modelValue || loading.value || !operation.current()) return
  const current = ++generation
  loading.value = true; failed.value = false; error.value = ''; candidate.value = null
  try {
    const result = await fulfillmentApi.getLocationCapability()
    if (current !== generation || !operation.current() || !props.modelValue) return
    capability.value = result
    if (!isAmapConfigured()) error.value = '本机尚未配置高德地图 Key，可继续手工填写地址。'
    else if (dialogOpened.value) await initMap(current)
  } catch { if (current === generation && operation.current()) failed.value = true }
  finally { if (current === generation && operation.current()) loading.value = false }
}
function onDialogOpened() {
  dialogOpened.value = true
  if (ready.value && !map) void initMap(generation)
}
function onDialogClosed() { dialogOpened.value = false; destroyMap() }
function search() {
  const keyword = searchText.value.trim()
  if (!geocoder || !keyword || searching.value) return
  searching.value = true; error.value = ''
  geocoder.getLocation(keyword, (status, result) => {
    searching.value = false
    const first = result.geocodes?.[0]
    if (status !== 'complete' || !first?.location) { error.value = '没有找到该地址，请补充城市、道路或建筑名称'; return }
    const [lng, lat] = coordinate(first.location)
    choose(lng, lat, first.formattedAddress || keyword); map?.setZoom(17)
  })
}
function confirm() {
  if (!candidate.value) return
  emit('picked', candidate.value); emit('update:modelValue', false)
}
watch(() => props.modelValue, open => {
  generation += 1; destroyMap(); dialogOpened.value = false; loading.value = false; capability.value = null; failed.value = false; error.value = ''; candidate.value = null
  if (open) void load()
})
</script>

<template>
  <el-dialog :model-value="modelValue" title="地址地图选点" width="760px" append-to-body destroy-on-close @opened="onDialogOpened" @closed="onDialogClosed" @update:model-value="emit('update:modelValue', $event)">
    <el-skeleton v-if="loading" :rows="5" animated />
    <template v-else>
      <el-alert v-if="availability.status !== 'READY' || error" :title="error || availability.message" type="warning" :closable="false" />
      <template v-if="ready">
        <div class="map-search"><el-input v-model="searchText" placeholder="输入城市、道路、园区或建筑名称" clearable @keyup.enter="search"/><el-button type="primary" :loading="searching" @click="search">搜索</el-button></div>
        <div :id="containerId" class="amap-container" />
        <p>点击地图选择准确收货点。地图地址只用于辅助回填，楼号、门牌和工地入口仍请人工核对。</p>
        <p v-if="candidate?.formattedAddress" class="candidate"><strong>已选：</strong>{{ candidate.formattedAddress }}（{{ candidate.longitude }}, {{ candidate.latitude }}）</p>
      </template>
      <p v-else>{{ addressLocationText(location) }}</p>
    </template>
    <template #footer><el-button @click="emit('update:modelValue', false)">继续手工填写</el-button><el-button v-if="ready" type="primary" :disabled="!candidate" @click="confirm">确认此位置</el-button></template>
  </el-dialog>
</template>

<style scoped>.map-search{display:flex;gap:10px;margin:0 0 12px}.amap-container{width:100%;height:410px;border:1px solid var(--line);border-radius:6px}.candidate{padding:10px 12px;background:#f0f7f5;color:#245c50}p{color:var(--muted);line-height:1.7;font-size:13px}</style>
