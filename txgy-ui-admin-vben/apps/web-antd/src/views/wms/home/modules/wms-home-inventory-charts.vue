<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type { InventoryChartItem } from './wms-home-inventory-chart-options';

import { computed, nextTick, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import { Card } from 'ant-design-vue';

import { getInventorySummary } from '#/api/wms/home';

import {
  buildInventoryChartItemList,
  formatQuantityText,
  getGoodsShareChartOptions,
  getWarehouseDistributionChartOptions,
  GOODS_SHARE_COLORS,
} from './wms-home-inventory-chart-options';

defineOptions({ name: 'WmsHomeInventoryCharts' });

const GOODS_SHARE_LIMIT = 5;
const WAREHOUSE_DISTRIBUTION_LIMIT = 8;

const loading = ref(false);
const totalQuantity = ref(0);
const goodsShareList = ref<InventoryChartItem[]>([]);
const warehouseDistributionList = ref<InventoryChartItem[]>([]);
const goodsShareChartRef = ref<EchartsUIType>();
const warehouseDistributionChartRef = ref<EchartsUIType>();
const { renderEcharts: renderGoodsShareEcharts } =
  useEcharts(goodsShareChartRef);
const { renderEcharts: renderWarehouseDistributionEcharts } = useEcharts(
  warehouseDistributionChartRef,
);

/**
 * 货物占比的图例数据（由组件用 HTML 渲染，不用 ECharts 内置 legend）。
 *
 * 卡片在 AI 首页只占约 3/10 宽，ECharts 图例不会自动换行/截断，
 * 文字会压到圆环上；改用 HTML 后截断与间距交给 CSS 控制。
 */
const goodsShareLegend = computed(() => {
  const list = goodsShareList.value;
  const total = list.reduce((sum, item) => sum + item.value, 0);
  return list.map((item, index) => ({
    color: GOODS_SHARE_COLORS[index % GOODS_SHARE_COLORS.length],
    name: item.name,
    percent: total > 0 ? ((item.value / total) * 100).toFixed(1) : '0.0',
  }));
});

/** 使用最新库存汇总数据渲染首页库存图表 */
async function renderCharts() {
  await nextTick();
  await Promise.all([
    renderGoodsShareEcharts(getGoodsShareChartOptions(goodsShareList.value)),
    renderWarehouseDistributionEcharts(
      getWarehouseDistributionChartOptions(warehouseDistributionList.value),
    ),
  ]);
}

/** 加载指定仓库的库存汇总和排行数据 */
async function load(warehouseId?: number) {
  loading.value = true;
  try {
    const data = await getInventorySummary({
      ...(warehouseId ? { warehouseId } : {}),
      goodsLimit: GOODS_SHARE_LIMIT,
      warehouseLimit: WAREHOUSE_DISTRIBUTION_LIMIT,
    });
    totalQuantity.value = Number(data.totalQuantity || 0);
    goodsShareList.value = buildInventoryChartItemList(
      data.goodsShareList,
      '未命名商品',
    );
    warehouseDistributionList.value = buildInventoryChartItemList(
      data.warehouseDistributionList,
      '未指定仓库',
    );
    await renderCharts();
  } finally {
    loading.value = false;
  }
}

defineExpose({ load });
</script>

<template>
  <div class="grid grid-cols-2 gap-4 max-lg:grid-cols-1">
    <Card :body-style="{ padding: '12px 16px 16px' }">
      <div class="mb-3">
        <div class="font-semibold">货物占比</div>
        <div class="text-sm text-muted-foreground">
          按商品库存数量汇总 Top 5
        </div>
      </div>
      <div class="flex items-center gap-3">
        <!-- 圆环图：占据剩余空间 -->
        <div class="relative min-h-[300px] min-w-0 flex-1">
          <EchartsUI ref="goodsShareChartRef" height="300px" />
          <div
            v-if="loading"
            class="absolute inset-0 flex items-center justify-center bg-card/70 text-sm text-muted-foreground"
          >
            加载中
          </div>
        </div>
        <!-- 图例：HTML 渲染，名称过长用省略号截断，不会压到圆环 -->
        <ul class="w-[46%] shrink-0 space-y-2 text-xs">
          <li
            v-for="item in goodsShareLegend"
            :key="item.name"
            class="flex items-center gap-1.5"
          >
            <span
              class="size-2.5 shrink-0 rounded-sm"
              :style="{ backgroundColor: item.color }"
            ></span>
            <span class="shrink-0 tabular-nums">{{ item.percent }}%</span>
            <span class="truncate text-muted-foreground" :title="item.name">
              {{ item.name }}
            </span>
          </li>
        </ul>
      </div>
    </Card>
    <Card :body-style="{ padding: '12px 16px 16px' }">
      <div class="mb-3 flex justify-between">
        <div>
          <div class="font-semibold">库存分布</div>
          <div class="text-sm text-muted-foreground">按仓库库存数量汇总</div>
        </div>
        <span class="font-semibold">
          总库存 {{ formatQuantityText(totalQuantity) }}
        </span>
      </div>
      <div class="relative min-h-[300px]">
        <EchartsUI ref="warehouseDistributionChartRef" height="300px" />
        <div
          v-if="loading"
          class="absolute inset-0 flex items-center justify-center bg-card/70 text-sm text-muted-foreground"
        >
          加载中
        </div>
      </div>
    </Card>
  </div>
</template>
