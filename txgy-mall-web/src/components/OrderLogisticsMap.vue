<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { isAmapConfigured, loadAmap, type AmapGeocoder, type AmapMap, type AmapOverlay } from '@/utils/amap'
import { hasAddressLocation, logisticsMapQueries } from '@/utils/fulfillment'
import type { AddressLocation, LogisticsEvent } from '@/types/fulfillment'

const props = defineProps<{ events: LogisticsEvent[]; receiverLocation?: AddressLocation; receiverAddress?: string; dispatchOriginAddress?: string }>()
const containerId = `amap-logistics-${Math.random().toString(36).slice(2)}`
const message = ref('正在整理物流节点…')
const ready = ref(false)
let map: AmapMap | undefined
let generation = 0
let renderedSignature = ''

function destroyMap() { generation += 1; map?.destroy(); map = undefined; ready.value = false }
function geocode(geocoder: AmapGeocoder, query: string): Promise<[number, number] | null> {
  return new Promise(resolve => {
    let settled = false, timer = 0
    const finish = (value: [number, number] | null) => { if (!settled) { settled = true; window.clearTimeout(timer); resolve(value) } }
    timer = window.setTimeout(() => finish(null), 8000)
    geocoder.getLocation(query, (status, result) => {
      const location = result.geocodes?.[0]?.location
      finish(status === 'complete' && location ? [location.getLng(), location.getLat()] : null)
    })
  })
}

async function renderMap() {
  const signature = [
    props.events.map(item => `${item.time || ''}|${item.content}`).join('\n'),
    props.receiverLocation?.longitude,
    props.receiverLocation?.latitude,
    props.receiverAddress,
    props.dispatchOriginAddress,
  ].join('|')
  if (signature === renderedSignature) return
  destroyMap()
  const current = generation
  const queries = logisticsMapQueries(props.events)
  const originAddress = props.dispatchOriginAddress?.trim()
  const hasReceiver = hasAddressLocation(props.receiverLocation) || Boolean(props.receiverAddress?.trim())
  if (!queries.length && !originAddress && !hasReceiver) {
    message.value = '暂无可用的物流或收货点位。'
    return
  }
  if (!isAmapConfigured()) { message.value = '地图服务未配置，文字物流轨迹不受影响。'; return }
  try {
    const amap = await loadAmap()
    if (current !== generation) return
    const geocoder = new amap.Geocoder({ radius: 1000 })
    const points: Array<{ position: [number, number]; label: string }> = []
    if (originAddress) {
      const position = await geocode(geocoder, originAddress)
      if (position && current === generation) points.push({ position, label: '店铺发货地' })
    }
    const located = await Promise.all(queries.map(async item => ({ item, position: await geocode(geocoder, item.query) })))
    if (current !== generation) return
    for (const { item, position } of located) {
      if (position && !points.some(point => point.position[0] === position[0] && point.position[1] === position[1])) points.push({ position, label: item.label })
    }
    if (hasAddressLocation(props.receiverLocation)) {
      const position: [number, number] = [props.receiverLocation!.longitude!, props.receiverLocation!.latitude!]
      if (!points.some(point => point.position[0] === position[0] && point.position[1] === position[1])) points.push({ position, label: '收货地址' })
    } else if (props.receiverAddress?.trim()) {
      const position = await geocode(geocoder, props.receiverAddress.trim())
      if (position && current === generation && !points.some(point => point.position[0] === position[0] && point.position[1] === position[1])) points.push({ position, label: '收货地址（文字定位）' })
    }
    if (current !== generation) return
    if (!points.length) {
      message.value = '暂未解析到可展示的点位；文字地址和物流轨迹不受影响。'
      renderedSignature = signature
      return
    }
    ready.value = true
    await nextTick()
    if (current !== generation) return
    const instance = new amap.Map(containerId, { zoom: 5, center: [108.93977, 34.341574], viewMode: '2D' })
    map = instance
    const overlays: AmapOverlay[] = points.map((point, index) => new amap.Marker({ position: point.position, title: `${index + 1}. ${point.label}` }))
    if (points.length > 1) overlays.push(new amap.Polyline({ path: points.map(point => point.position), strokeColor: '#168572', strokeWeight: 5, strokeOpacity: 0.8, lineJoin: 'round', strokeStyle: 'dashed' }))
    instance.add(overlays)
    instance.setFitView(overlays, false, [55, 55, 55, 55], 12)
    renderedSignature = signature
    message.value = points.length > 1
      ? (queries.length ? `已展示 ${points.length} 个可定位节点。虚线仅表示节点先后，不代表车辆实际行驶路线。` : '已展示店铺发货地至收货地址的预计路线，不代表承运商实际行驶轨迹。')
      : '已展示收货点位；暂无完整发货地或承运商轨迹，不绘制物流路线。'
  } catch { if (current === generation) message.value = '物流地图加载失败，文字轨迹仍可正常查看。' }
}

watch(() => [props.events.map(item => `${item.time || ''}|${item.content}`).join('\n'), props.receiverLocation?.longitude, props.receiverLocation?.latitude, props.receiverAddress, props.dispatchOriginAddress].join('|'), () => void renderMap(), { immediate: true })
onBeforeUnmount(destroyMap)
</script>

<template>
  <section class="route-map" aria-label="物流节点地图">
    <h3>物流节点地图</h3>
    <p>{{ message }}</p>
    <div v-show="ready" :id="containerId" class="map-container" />
    <small>无承运商轨迹时展示店铺登记发货地至收货地址的预计路线；最终以承运商文字轨迹和实际签收为准。</small>
  </section>
</template>

<style scoped>
.route-map{margin:18px 0;padding:16px;border:1px solid #dce7e4;background:#f8fbfa;border-radius:6px}.route-map h3{margin:0 0 8px;font-size:15px}.route-map p,.route-map small{color:var(--muted);font-size:12px;line-height:1.7}.map-container{width:100%;height:330px;margin:12px 0;border:1px solid var(--line);border-radius:6px;background:#f2f5f4}
</style>
