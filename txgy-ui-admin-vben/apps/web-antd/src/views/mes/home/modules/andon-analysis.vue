<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getAndonAnalysis } from '#/api/mes/home';

import { getAndonReasonChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeAndonAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const activeCount = ref(0); // 未处置数
const handleRate = ref(0); // 处置率（%）
const avgHandleMinutes = ref(0); // 平均处置时长（分钟）

/** 加载安灯呼叫分析数据并渲染原因 TOP5 横向柱状图 */
async function loadData() {
  const data = await getAndonAnalysis(30);
  activeCount.value = data.activeCount;
  handleRate.value = data.handleRate;
  avgHandleMinutes.value = data.avgHandleMinutes;
  await renderEcharts(
    getAndonReasonChartOptions(
      data.topReasons.map((d) => d.reason),
      data.topReasons.map((d) => d.count),
    ),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="安灯呼叫分析（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-red-500">{{ activeCount }}</div>
        <div class="text-xs text-gray-500">未处置</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-green-600">
          {{ handleRate }}%
        </div>
        <div class="text-xs text-gray-500">处置率</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-blue-500">
          {{ avgHandleMinutes }}
        </div>
        <div class="text-xs text-gray-500">平均处置(分钟)</div>
      </div>
    </div>
    <EchartsUI ref="chartRef" class="h-[210px] w-full" />
  </Card>
</template>
