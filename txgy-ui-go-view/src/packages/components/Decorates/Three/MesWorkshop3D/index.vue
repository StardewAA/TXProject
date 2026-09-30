<template>
  <div ref="chartRef" class="mes-workshop-3d"></div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, PropType, ref, toRefs, watch } from 'vue'
import { CreateComponentType } from '@/packages/index.d'
import { option } from './config'
import { World } from './code/World'
import throttle from 'lodash/throttle'

const props = defineProps({
  chartConfig: {
    type: Object as PropType<CreateComponentType & typeof option>,
    required: true
  }
})

const chartRef = ref<HTMLElement>()
const { w, h } = toRefs(props.chartConfig.attr)
let world: World | null = null

const init = () => {
  const dom: HTMLElement | undefined = chartRef.value
  if (!dom) return
  const conf = props.chartConfig.option || ({} as typeof option)
  world = new World({
    dom,
    width: w.value,
    height: h.value,
    routeId: conf.routeId || 1,
    autoRotate: conf.autoRotate === true,
    pollInterval: conf.pollInterval === undefined ? 30000 : conf.pollInterval * 1000
  })
}

// 尺寸变化时同步渲染器与相机
watch(
  () => [w.value, h.value],
  throttle(() => {
    world && world.updateSize(w.value, h.value)
  }, 120)
)

onMounted(() => {
  try {
    init()
  } catch (error) {
    console.error('[MesWorkshop3D] 场景初始化失败', error)
  }
})

onUnmounted(() => {
  if (world) world.dispose()
  world = null
})
</script>

<style scoped>
.mes-workshop-3d {
  width: 100%;
  height: 100%;
}
</style>
