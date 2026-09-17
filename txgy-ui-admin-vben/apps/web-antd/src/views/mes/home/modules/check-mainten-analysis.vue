<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getCheckMainten } from '#/api/mes/home';

import { getCheckMaintenChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeCheckMaintenAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const planEnabledCount = ref(0); // 已启用方案数
const checkFinishRate = ref(0); // 点检完成率（%）
const maintenSubmitRate = ref(0); // 保养提交率（%）

/** 加载点检保养执行数据并渲染柱状图 */
async function loadData() {
  const data = await getCheckMainten(30);
  planEnabledCount.value = data.planEnabledCount;
  checkFinishRate.value = data.checkFinishRate;
  maintenSubmitRate.value = data.maintenSubmitRate;
  await renderEcharts(
    getCheckMaintenChartOptions(
      ['点检记录', '保养记录'],
      [data.checkTotalCount, data.maintenTotalCount],
      [data.checkFinishedCount, data.maintenSubmittedCount],
    ),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="点检保养执行（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-blue-500">
          {{ planEnabledCount }}
        </div>
        <div class="text-xs text-gray-500">已启用方案</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-green-600">
          {{ checkFinishRate }}%
        </div>
        <div class="text-xs text-gray-500">点检完成率</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-orange-500">
          {{ maintenSubmitRate }}%
        </div>
        <div class="text-xs text-gray-500">保养提交率</div>
      </div>
    </div>
    <EchartsUI ref="chartRef" class="h-[210px] w-full" />
  </Card>
</template>
