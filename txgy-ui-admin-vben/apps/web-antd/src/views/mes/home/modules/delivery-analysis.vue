<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card, Empty } from 'ant-design-vue';

import { getDeliveryAnalysis } from '#/api/mes/home';

import { getDeliveryPieChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeDeliveryAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const onTimeRate = ref(0); // 按期完成率（%）
const overdueOpenCount = ref(0); // 在制超期预警数
const avgOverdueDays = ref(0); // 平均延期天数
const hasData = ref(false); // 近 N 天是否有完成工单

/** 加载生产交付达成数据并渲染饼图 */
async function loadData() {
  const data = await getDeliveryAnalysis(30);
  onTimeRate.value = data.onTimeRate;
  overdueOpenCount.value = data.overdueOpenCount;
  avgOverdueDays.value = data.avgOverdueDays;
  hasData.value = data.totalCount > 0;
  if (!hasData.value) {
    return;
  }
  await renderEcharts(
    getDeliveryPieChartOptions(data.onTimeCount, data.overdueCount),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="生产交付达成（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-green-600">
          {{ onTimeRate }}%
        </div>
        <div class="text-xs text-gray-500">按期完成率</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-red-500">
          {{ overdueOpenCount }}
        </div>
        <div class="text-xs text-gray-500">在制超期预警</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-orange-500">
          {{ avgOverdueDays }}
        </div>
        <div class="text-xs text-gray-500">平均延期天数</div>
      </div>
    </div>
    <EchartsUI v-if="hasData" ref="chartRef" class="h-[210px] w-full" />
    <Empty v-else class="h-[210px]" description="近 30 天暂无完成工单" />
  </Card>
</template>
