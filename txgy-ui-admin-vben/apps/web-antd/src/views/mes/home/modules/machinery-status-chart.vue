<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getMachineryStatusDistribution } from '#/api/mes/home';

import { getMachineryStatusChartOptions } from '../chart-options';
import { MACHINERY_STATUS_COLOR_MAP } from '../data';

defineOptions({ name: 'MesHomeMachineryStatusChart' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

/** 加载设备状态分布数据并渲染饼图 */
async function loadData() {
  const data = await getMachineryStatusDistribution();
  const chartData = data.map((d) => ({
    itemStyle: { color: MACHINERY_STATUS_COLOR_MAP[d.status] || '#409EFF' },
    name: d.statusName,
    value: d.count,
  }));
  await renderEcharts(getMachineryStatusChartOptions(chartData));
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="设备状态分布" class="h-full">
    <EchartsUI ref="chartRef" class="h-[280px] w-full" />
  </Card>
</template>
