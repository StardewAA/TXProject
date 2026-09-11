<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getRepairAnalysis } from '#/api/mes/home';

import { getMachineryStatusChartOptions } from '../chart-options';
import { REPAIR_STATUS_COLOR_MAP } from '../data';

defineOptions({ name: 'MesHomeRepairAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const pendingCount = ref(0); // 待处理数
const finishedCount = ref(0); // 已完成数
const avgRepairHours = ref(0); // 平均维修时长（小时）

/** 加载维修工单分析数据并渲染状态分布饼图 */
async function loadData() {
  const data = await getRepairAnalysis(30);
  pendingCount.value = data.pendingCount;
  finishedCount.value = data.finishedCount;
  avgRepairHours.value = data.avgRepairHours;
  const chartData = data.statusStats.map((d) => ({
    itemStyle: { color: REPAIR_STATUS_COLOR_MAP[d.status] || '#409EFF' },
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
  <Card title="维修工单分析（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-orange-500">{{ pendingCount }}</div>
        <div class="text-xs text-gray-500">待处理</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-green-600">{{ finishedCount }}</div>
        <div class="text-xs text-gray-500">已完成</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-blue-500">
          {{ avgRepairHours }}
        </div>
        <div class="text-xs text-gray-500">平均维修(小时)</div>
      </div>
    </div>
    <EchartsUI ref="chartRef" class="h-[210px] w-full" />
  </Card>
</template>
