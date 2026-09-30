<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';

import { IconifyIcon } from '@vben/icons';

import { loadBaiduMapSdk } from '#/components/map/src/utils';

/**
 * 职位页右栏「工作地址」页签的地图块。
 *
 * 与 components/location-card.vue 的区别：那块用在职位详情页/企业页，偏「地址卡片」；
 * 这块是嵌在职位详情面板里的嵌入式地图，地址压在图上、动作浮在图上，
 * 配色跟随职位页的蓝绿体系，不改动共享组件的青色主题。
 */
const props = defineProps<{
  address?: string;
  latitude?: number;
  longitude?: number;
}>();

const mapElement = ref<HTMLElement>();
const mapState = ref<'empty' | 'fallback' | 'loading' | 'ready'>('loading');
const mapMessage = ref('');
let mapInstance: any;

const addressText = computed(() => props.address?.trim() || '');
const hasCoordinate = computed(
  () => props.latitude != null && props.longitude != null,
);
const hasLocation = computed(() => Boolean(addressText.value) || hasCoordinate.value);

const baiduHref = computed(() => {
  const params = new URLSearchParams({
    output: 'html',
    coord_type: 'bd09ll',
    src: 'webapp.txgy.hrm',
  });
  if (hasCoordinate.value) {
    params.set('location', `${props.latitude},${props.longitude}`);
    params.set('title', addressText.value || '工作地点');
    params.set('content', addressText.value || '工作地点');
    return `https://api.map.baidu.com/marker?${params.toString()}`;
  }
  params.set('address', addressText.value || '工作地点待确认');
  return `https://api.map.baidu.com/geocoder?${params.toString()}`;
});

/** 优先用经纬度描点；只有文字地址时退回地理编码，让纯地址也能落到地图上。 */
function resolvePoint() {
  if (hasCoordinate.value) {
    return Promise.resolve(
      new window.BMapGL.Point(props.longitude, props.latitude),
    );
  }
  return new Promise<any>((resolve, reject) => {
    const timeoutId = setTimeout(
      () => reject(new Error('GEOCODER_TIMEOUT')),
      8000,
    );
    new window.BMapGL.Geocoder().getPoint(
      addressText.value,
      (point: any) => {
        clearTimeout(timeoutId);
        if (point) resolve(point);
        else reject(new Error('GEOCODER_NO_MATCH'));
      },
    );
  });
}

/**
 * 等真正要用的构造器就位。
 *
 * SDK 的 callback 只保证 BMapGL 这个命名空间出现，子类（Point / Map / Marker…）
 * 是引导脚本随后才挂上去的，冷启动时这两步之间有明显间隙，此时 new BMapGL.Point
 * 会抛「Point is not a constructor」。loadBaiduMapSdk 里 `if (window.BMapGL) return`
 * 的短路同理，可能撞上刚建好名字、还没挂子类的半成品。
 */
function waitForBMapGl(timeout = 8000) {
  return new Promise<void>((resolve, reject) => {
    const deadline = Date.now() + timeout;
    const tick = () => {
      const gl = window.BMapGL;
      if (typeof gl?.Point === 'function' && typeof gl?.Map === 'function') {
        resolve();
      } else if (Date.now() > deadline) {
        reject(new Error('BMAPGL_INCOMPLETE'));
      } else {
        setTimeout(tick, 50);
      }
    };
    tick();
  });
}

async function renderMap() {
  if (!hasLocation.value) {
    mapInstance?.destroy?.();
    mapInstance = undefined;
    mapState.value = 'empty';
    mapMessage.value = '招聘方暂未填写工作地址';
    return;
  }

  try {
    await loadBaiduMapSdk();
    await waitForBMapGl();
    const point = await resolvePoint();

    // 已经建过图就只挪中心点、换标记，不整张重建：
    // 重建会打断 SDK 尚在进行的瓦片回调（控制台里会冒出它的内部报错），
    // 视觉上也会白闪一下。右侧详情面板不再换页签，切换职位时组件是复用的，
    // 所以这条「原地更新」的路径才是常态。
    if (mapInstance) {
      mapInstance.setCenter(point);
      mapInstance.clearOverlays();
      mapInstance.addOverlay(new window.BMapGL.Marker(point));
      mapState.value = 'ready';
      return;
    }

    mapState.value = 'loading';
    mapMessage.value = '';
    // 先切到 ready 让地图容器挂载，再实例化，否则拿不到可测量的 DOM 节点
    mapState.value = 'ready';
    await nextTick();
    if (!mapElement.value) return;
    mapInstance = new window.BMapGL.Map(mapElement.value);
    mapInstance.centerAndZoom(point, 15);
    mapInstance.addOverlay(new window.BMapGL.Marker(point));
    mapInstance.addControl(new window.BMapGL.ScaleControl());
    mapInstance.disableScrollWheelZoom();
  } catch (error) {
    console.warn('[RecruitJobLocation] 地图渲染失败', error);
    const code = error instanceof Error ? error.message : '';
    mapState.value = 'fallback';
    mapMessage.value = code.includes('未配置')
      ? '地图服务未配置，暂时无法显示地图。'
      : code === 'GEOCODER_NO_MATCH'
        ? '未能在百度地图中定位该地址，可点击下方按钮搜索。'
        : code === 'BMAPGL_INCOMPLETE'
          ? '地图脚本尚未就绪，请点击重试。'
          : '地图加载失败，请检查网络后重试。';
  }
}

// 换职位时组件是复用的（详情面板已无页签），地址变化要主动跟着走
watch(
  () => [props.address, props.latitude, props.longitude],
  () => void renderMap(),
);

onMounted(() => void renderMap());

onUnmounted(() => mapInstance?.destroy?.());
</script>

<template>
  <section class="job-location">
    <p class="job-location-address">
      <IconifyIcon icon="lucide:map-pin" />
      <span>{{ addressText || '招聘方暂未填写工作地址' }}</span>
    </p>

    <div class="job-location-frame">
      <div v-if="mapState === 'loading'" class="job-location-placeholder">
        地图加载中…
      </div>
      <div
        v-else-if="mapState === 'ready'"
        ref="mapElement"
        class="job-location-canvas"
      />
      <div v-else class="job-location-placeholder">
        <IconifyIcon icon="lucide:map" />
        <span>{{ mapMessage }}</span>
        <button
          v-if="mapState === 'fallback' && hasLocation"
          type="button"
          class="job-location-retry"
          @click="renderMap"
        >
          重试加载
        </button>
      </div>

      <a
        v-if="mapState === 'ready'"
        class="job-location-pin"
        :href="baiduHref"
        target="_blank"
        rel="noopener noreferrer"
      >
        <IconifyIcon icon="lucide:navigation" /> 点击查看地图
      </a>
    </div>

    <!-- 说明与外部链接同排：省下一行高度，也不丢信息 -->
    <div class="job-location-foot">
      <p v-if="mapState === 'ready'" class="job-location-hint">
        地图按企业登记地址定位，具体工作地点请与招聘方确认。
      </p>
      <span v-else class="job-location-hint" />
      <a
        v-if="mapState !== 'empty'"
        class="job-location-more"
        :href="baiduHref"
        target="_blank"
        rel="noopener noreferrer"
      >
        在百度地图查看更多信息
        <IconifyIcon icon="lucide:arrow-up-right" />
      </a>
    </div>
  </section>
</template>

<style scoped>
/* 变量由 .recruit-page--jobs 定义，自定义属性可穿透组件作用域继承 */
.job-location {
  display: grid;
  gap: 14px;
}

.job-location-address {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0;
  color: var(--job-ink);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.6;
}

/* 定位符是标签不是动作，跟结构蓝走；绿在本页只留给「立即沟通」 */
.job-location-address :deep(svg) {
  flex: none;
  margin-top: 3px;
  color: var(--job-blue);
}

.job-location-frame {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--job-line);
  border-radius: 12px;
  background: var(--job-blue-soft);
}

.job-location-canvas {
  height: 160px;
}

.job-location-placeholder {
  display: flex;
  min-height: 160px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--job-muted);
  font-size: 13px;
  text-align: center;
}

.job-location-placeholder :deep(svg) {
  color: #9dc4e8;
  font-size: 20px;
}

.job-location-retry {
  padding: 4px 12px;
  border: 1px solid var(--job-blue);
  border-radius: 999px;
  color: var(--job-blue);
  background: #fff;
  cursor: pointer;
  font-size: 12px;
}

.job-location-retry:hover {
  background: var(--job-blue-soft);
}

/* 动作浮在图上，而不是压在图下面另占一行。
   靠右上角：左下角是百度 Logo、右下角是版权行，两处都不能压。 */
.job-location-pin {
  position: absolute;
  top: 12px;
  right: 12px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 999px;
  background: rgb(255 255 255 / 94%);
  box-shadow: 0 4px 14px rgb(27 42 61 / 16%);
  color: var(--job-blue);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: color 140ms ease, background-color 140ms ease;
}

.job-location-pin:hover {
  color: var(--job-green);
  background: #fff;
}

.job-location-pin:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

/* 说明占满剩余宽度，链接靠右；两者同一行 */
.job-location-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.job-location-hint {
  margin: 0;
  color: var(--job-muted);
  font-size: 12px;
  line-height: 1.6;
}

.job-location-more {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border: 1px solid var(--job-line);
  border-radius: 10px;
  color: var(--job-blue);
  background: #fff;
  font-size: 13px;
  font-weight: 500;
  text-decoration: none;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.job-location-more:hover {
  border-color: var(--job-blue);
  background: var(--job-blue-soft);
}

.job-location-more:focus-visible {
  outline: 2px solid var(--job-blue);
  outline-offset: 2px;
}

@media (max-width: 760px) {
  .job-location-canvas,
  .job-location-placeholder {
    height: 180px;
    min-height: 180px;
  }
}
</style>
