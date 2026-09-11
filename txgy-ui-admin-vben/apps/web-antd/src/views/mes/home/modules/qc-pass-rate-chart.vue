<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getQcPassRate } from '#/api/mes/home';

import { getQcPassRateChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeQcPassRateChart' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

/** 加载质检合格率数据并渲染柱状图（默认统计近 30 天） */
async function loadData() {
  const data = await getQcPassRate(30);
  await renderEcharts(
    getQcPassRateChartOptions(
      data.map((d) => d.qcTypeName),
      data.map((d) => d.passRate),
      data.map((d) => d.docPassRate),
    ),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="质检合格率（近 30 天）" class="h-full">
    <EchartsUI ref="chartRef" class="h-[280px] w-full" />
  </Card>
</template>
