<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { loadBaiduMapSdk } from '#/components/map/src/utils';

const props = withDefaults(defineProps<{
  address?: string;
  latitude?: number;
  longitude?: number;
  compact?: boolean;
}>(), { compact: false });

const mapElement = ref<HTMLElement>();
const mapState = ref<'loading' | 'ready' | 'fallback'>('loading');
const fallbackMessage = ref('');
const retryable = ref(false);
const baiduHref = computed(() => {
  const params = new URLSearchParams({
    output: 'html',
    coord_type: 'bd09ll',
    src: 'webapp.txgy.hrm',
  });
  if (props.longitude != null && props.latitude != null) {
    params.set('location', `${props.latitude},${props.longitude}`);
    params.set('title', props.address || '工作地点');
    params.set('content', props.address || '工作地点');
    return `https://api.map.baidu.com/marker?${params.toString()}`;
  }
  params.set('address', props.address || '工作地点待确认');
  return `https://api.map.baidu.com/geocoder?${params.toString()}`;
});
let mapInstance: any;

function resolveMapPoint() {
  if (props.longitude != null && props.latitude != null) {
    return Promise.resolve(new window.BMapGL.Point(props.longitude, props.latitude));
  }
  if (!props.address || props.address === '工作地点待确认') {
    return Promise.reject(new Error('招聘方尚未填写可定位的工作地址'));
  }
  return new Promise<any>((resolve, reject) => {
    const timeoutId = setTimeout(() => reject(new Error('地址解析超时，请检查网络后重试')), 8000);
    new window.BMapGL.Geocoder().getPoint(props.address, (point: any) => {
      clearTimeout(timeoutId);
      if (point) resolve(point);
      else reject(new Error('百度地图未能匹配该工作地址'));
    });
  });
}

async function renderMap() {
  mapState.value = 'loading';
  fallbackMessage.value = '';
  retryable.value = false;
  try {
    await loadBaiduMapSdk();
    const point = await resolveMapPoint();
    mapState.value = 'ready';
    await nextTick();
    if (!mapElement.value) return;
    mapInstance = new window.BMapGL.Map(mapElement.value);
    mapInstance.centerAndZoom(point, 15);
    mapInstance.addOverlay(new window.BMapGL.Marker(point));
    mapInstance.addControl(new window.BMapGL.ScaleControl());
    mapInstance.disableScrollWheelZoom();
    mapState.value = 'ready';
  } catch (error) {
    console.warn('[PortalRecruitLocation] 百度地图无法显示', error);
    const message = error instanceof Error ? error.message : '';
    fallbackMessage.value = message.includes('尚未填写')
      ? '招聘方尚未提供可定位的工作地址。'
      : message.includes('未能匹配')
        ? '百度地图未能识别该地址，请核对详细地址。'
        : message.includes('未配置')
          ? '地图服务未配置，暂时无法加载地图。'
          : '百度地图加载失败，请检查网络或 AK 的域名白名单后重试。';
    retryable.value = !message.includes('尚未填写') && !message.includes('未能匹配');
    mapState.value = 'fallback';
  }
}

onMounted(() => void renderMap());

onUnmounted(() => mapInstance?.destroy?.());
</script>

<template>
  <section class="location-card">
    <div class="location-address"><IconifyIcon icon="lucide:map-pin" /><span>{{ address || '工作地点待确认' }}</span></div>
    <div v-if="mapState === 'loading'" class="location-map location-loading">地图加载中…</div>
    <div v-else-if="mapState === 'ready'" ref="mapElement" class="location-map" :class="{ compact }" />
    <div v-else class="location-fallback"><IconifyIcon icon="lucide:map" /><span>{{ fallbackMessage }}</span><button v-if="retryable" type="button" @click="renderMap">重试加载</button></div>
    <div class="location-actions"><a :href="baiduHref" target="_blank" rel="noopener noreferrer" class="location-primary"><IconifyIcon icon="lucide:navigation" /> 百度地图查看位置</a></div>
  </section>
</template>

<style scoped>
/*
 * 这个组件被职位详情页和企业页共用，两页的配色体系不一样：
 * 职位详情页（.recruit-detail-page）定义了职位页那套 --job-* 变量，企业页没有。
 * 所以每个色值都写成 var(--job-*, 原青色值) —— 在职位详情页跟着职位页走，
 * 在企业页拿不到变量就退回原来的青色，那边一个像素都不会变。
 */
.location-card {
  overflow: hidden;
  border: 1px solid var(--job-line, #d4e7ef);
  border-radius: 12px;
  background: #fff;
}

.location-address {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--job-line, #e7f1f5);
  color: var(--job-ink, #123f49);
  font-weight: 600;
  line-height: 1.6;
}

/* 定位符是标签不是动作，跟结构蓝走（与 job-location-map 同一口径） */
.location-address :deep(svg) {
  flex: none;
  margin-top: 3px;
  color: var(--job-blue, #0f8f8c);
}

.location-map {
  height: 360px;
}

.location-map.compact {
  height: 200px;
}

.location-loading,
.location-fallback {
  display: flex;
  min-height: 240px;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--job-blue-soft, #f5fbfc);
  color: var(--job-muted, #7593a0);
}

.location-actions {
  display: flex;
  justify-content: flex-end;
  padding: 12px 18px;
  border-top: 1px solid var(--job-line, #e7f1f5);
}

/* 「去百度地图看」是导航不是主 CTA，走结构蓝不占绿 ——
   绿在职位页只留给「立即沟通」。 */
.location-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--job-blue, linear-gradient(135deg, #0f9f9b, #2f80ed));
  box-shadow: 0 6px 16px color-mix(in srgb, var(--job-blue, #0f8f8c) 22%, transparent);
  color: #fff;
  text-decoration: none;
  transition: transform 0.18s, background-color 0.18s;
}

.location-primary:hover {
  transform: translateY(-1px);
  color: #fff;
}

@media (max-width: 680px) {
  .location-map {
    height: 280px;
  }

  .location-actions {
    width: 100%;
  }

  .location-primary {
    flex: 1;
    justify-content: center;
  }
}
</style>
