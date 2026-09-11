<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card, Empty } from 'ant-design-vue';

import { getDefectAnalysis } from '#/api/mes/home';

import { getDefectTopChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeDefectAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const totalQuantity = ref(0); // 缺陷总数量
const criticalQuantity = ref(0); // 致命缺陷数量
const majorQuantity = ref(0); // 严重缺陷数量
const hasData = ref(false); // 是否有缺陷数据

/** 加载不良缺陷数据并渲染 TOP 柱状图 */
async function loadData() {
  const data = await getDefectAnalysis(30);
  totalQuantity.value = data.totalQuantity;
  criticalQuantity.value = data.criticalQuantity;
  majorQuantity.value = data.majorQuantity;
  hasData.value = data.defectTopList.length > 0;
  if (!hasData.value) {
    return;
  }
  await renderEcharts(
    getDefectTopChartOptions(
      data.defectTopList.map((item) => item.name),
      data.defectTopList.map((item) => item.quantity),
    ),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="不良缺陷分析（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-red-500">
          {{ totalQuantity }}
        </div>
        <div class="text-xs text-gray-500">缺陷总数</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-orange-600">
          {{ criticalQuantity }}
        </div>
        <div class="text-xs text-gray-500">致命缺陷</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-yellow-600">
          {{ majorQuantity }}
        </div>
        <div class="text-xs text-gray-500">严重缺陷</div>
      </div>
    </div>
    <EchartsUI v-if="hasData" ref="chartRef" class="h-[210px] w-full" />
    <Empty v-else class="h-[210px]" description="近 30 天暂无缺陷记录" />
  </Card>
</template>
