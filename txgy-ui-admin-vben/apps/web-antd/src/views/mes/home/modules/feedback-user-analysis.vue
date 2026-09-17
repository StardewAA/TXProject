<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import { onMounted, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card, Empty } from 'ant-design-vue';

import { getFeedbackUserAnalysis } from '#/api/mes/home';

import { getFeedbackUserChartOptions } from '../chart-options';

defineOptions({ name: 'MesHomeFeedbackUserAnalysis' });

const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);

const userCount = ref(0); // 参与报工人数
const totalQuantity = ref(0); // 报工总量
const passRate = ref(0); // 合格率（%）
const hasData = ref(false); // 近 N 天是否有报工记录

/** 加载人员报工产出数据并渲染 TOP 柱状图 */
async function loadData() {
  const data = await getFeedbackUserAnalysis(30);
  userCount.value = data.userCount;
  totalQuantity.value = data.totalQuantity;
  passRate.value = data.passRate;
  hasData.value = data.topList.length > 0;
  if (!hasData.value) {
    return;
  }
  await renderEcharts(
    getFeedbackUserChartOptions(
      data.topList.map((item) => item.userName),
      data.topList.map((item) => item.qualifiedQuantity),
    ),
  );
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <Card title="人员报工产出（近 30 天）" class="h-full">
    <div class="mb-2 flex justify-around">
      <div class="text-center">
        <div class="text-lg font-semibold text-blue-500">
          {{ userCount }}
        </div>
        <div class="text-xs text-gray-500">参与报工人数</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-green-600">
          {{ totalQuantity }}
        </div>
        <div class="text-xs text-gray-500">报工总量</div>
      </div>
      <div class="text-center">
        <div class="text-lg font-semibold text-orange-500">
          {{ passRate }}%
        </div>
        <div class="text-xs text-gray-500">合格率</div>
      </div>
    </div>
    <EchartsUI v-if="hasData" ref="chartRef" class="h-[210px] w-full" />
    <Empty v-else class="h-[210px]" description="近 30 天暂无报工记录" />
  </Card>
</template>
