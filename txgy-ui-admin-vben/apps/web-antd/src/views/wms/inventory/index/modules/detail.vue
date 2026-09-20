<script lang="ts" setup>
import type { EChartsOption, EchartsUIType } from '@vben/plugins/echarts';
import type { WmsInventoryApi } from '#/api/wms/inventory';

import { computed, nextTick, onBeforeUnmount, ref } from 'vue';

import { EchartsUI, useEcharts } from '@vben/plugins/echarts';
import { useVbenModal } from '@vben/common-ui';
import { formatDateTime } from '@vben/utils';

import type { TableColumnsType } from 'ant-design-vue';

import { Card, Segmented, Spin, Table } from 'ant-design-vue';

import { getInventoryDetailList } from '#/api/wms/inventory';
import { getInventoryHistoryTrend } from '#/api/wms/inventory/history';
import { formatQuantity } from '#/views/wms/utils/format';

interface TrendSeries {
  warehouseId?: number;
  warehouseName: string;
  points: Array<{ beforeQuantity: number; quantity: number; time: string }>;
}

interface TooltipParam {
  axisValueLabel?: string;
  data?: { change?: number | null; value?: number | null };
  marker?: string;
  seriesName?: string;
}

function getTimestamp(value?: Date | number | string) {
  if (!value) return 0;
  return typeof value === 'number' ? value : new Date(value).getTime();
}

defineOptions({ name: 'WmsInventoryDetail' });

const detailData = ref<WmsInventoryApi.Inventory>({});
const inventoryRows = ref<WmsInventoryApi.Inventory[]>([]);
const trendSeries = ref<TrendSeries[]>([]);
const activeWarehouseId = ref<number>();
const loading = ref(false);
const chartRef = ref<EchartsUIType>();
const { renderEcharts } = useEcharts(chartRef);
let refreshTimer: ReturnType<typeof setInterval> | undefined;

const warehouseOptions = computed(() => [
  { label: '全部仓库', value: 0 },
  ...inventoryRows.value.map((row) => ({
    label: row.warehouseName || `仓库 ${row.warehouseId || '-'}`,
    value: row.warehouseId || 0,
  })),
]);

const currentQuantity = computed(() =>
  inventoryRows.value.reduce((sum, row) => sum + Number(row.quantity || 0), 0),
);

function getMonthKeys(count = 12) {
  const now = new Date();
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(now.getFullYear(), now.getMonth() - count + index + 1, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
  });
}

function getMonthlySnapshots(item: TrendSeries, months: string[]) {
  const points = item.points
    .slice()
    .sort((a, b) => getTimestamp(a.time) - getTimestamp(b.time));
  const initialQuantity = points[0]?.beforeQuantity;
  return months.map((month) => {
    const [year, monthNumber] = month.split('-').map(Number);
    const monthEnd = new Date(year!, monthNumber!, 0, 23, 59, 59, 999).getTime();
    const latest = points.filter((point) => getTimestamp(point.time) <= monthEnd).at(-1);
    return latest?.quantity ?? initialQuantity ?? null;
  });
}

const trendConclusion = computed(() => {
  const months = getMonthKeys();
  const selectedSeries = activeWarehouseId.value
    ? trendSeries.value.filter(
        (item) => item.warehouseId === activeWarehouseId.value,
      )
    : trendSeries.value;
  const totals = months.map((_, index) => {
    const values = selectedSeries.map((item) =>
      getMonthlySnapshots(item, months)[index],
    );
    return values.some((value) => value === null)
      ? null
      : values.reduce((sum, value) => sum + Number(value || 0), 0);
  });
  const validValues = totals.filter((value): value is number => value !== null);
  if (validValues.length < 2) return '暂无足够的月度库存数据';
  const change = validValues.at(-1)! - validValues[0]!;
  if (change < 0) {
    return `近 12 个月库存净下降 ${formatQuantity(Math.abs(change))}，呈高消耗/畅销倾向`;
  }
  if (change > 0) {
    return `近 12 个月库存净增加 ${formatQuantity(change)}，期间可能存在集中补货`;
  }
  return '近 12 个月库存总体平稳';
});

const tableColumns: TableColumnsType = [
  { title: '仓库编号', dataIndex: 'warehouseId', width: 110 },
  { title: '仓库名称', dataIndex: 'warehouseName', width: 180 },
  { title: '当前库存', dataIndex: 'quantity', align: 'right', width: 140 },
  { title: '计量单位', dataIndex: 'unit', width: 100 },
  { title: '最近更新时间', dataIndex: 'createTime', width: 180 },
];

function buildChartOptions(seriesList: TrendSeries[]): EChartsOption {
  const months = getMonthKeys();
  const selectedSeries = activeWarehouseId.value
    ? seriesList.filter((item) => item.warehouseId === activeWarehouseId.value)
    : seriesList;
  const warehouseSnapshots = selectedSeries.map((item) => ({
    ...item,
    values: getMonthlySnapshots(item, months),
  }));
  const visibleSeries = activeWarehouseId.value
    ? warehouseSnapshots
    : [
        {
          warehouseId: 0,
          warehouseName: '商品总库存',
          points: [],
          values: months.map((_, index) => {
            const values = warehouseSnapshots.map((item) => item.values[index]);
            return values.some((value) => value === null)
              ? null
              : values.reduce((sum, value) => sum + Number(value || 0), 0);
          }),
        },
      ];
  return {
    color: ['#1677ff', '#18a058', '#f59e0b', '#7c3aed', '#e11d48'],
    grid: { bottom: 30, containLabel: true, left: 24, right: 24, top: 36 },
    legend: { itemHeight: 10, itemWidth: 10, top: 4 },
    tooltip: {
      trigger: 'axis',
      formatter: (params: unknown) => {
        const entries = (Array.isArray(params) ? params : [params]) as TooltipParam[];
        const title = entries[0]?.axisValueLabel || '';
        return [
          `<b>${title}</b>`,
          ...entries.map((entry) => {
            const value = entry.data?.value;
            const change = entry.data?.change;
            const changeText =
              change === null || change === undefined
                ? '首月'
                : `较上月 ${change > 0 ? '+' : ''}${formatQuantity(change)}`;
            return `${entry.marker || ''}${entry.seriesName}：${formatQuantity(value)} ${detailData.value.unit || ''}<br/>${changeText}`;
          }),
        ].join('<br/>');
      },
    },
    xAxis: { type: 'category', name: '月份', data: months, axisTick: { show: false } },
    yAxis: { type: 'value', name: `库存数量${detailData.value.unit ? `（${detailData.value.unit}）` : ''}`, minInterval: 1 },
    series: visibleSeries.map((item) => ({
      name: item.warehouseName,
      type: 'line',
      smooth: false,
      showSymbol: true,
      data: item.values.map((value, index) => ({
        value,
        change:
          index === 0 || value === null || item.values[index - 1] === null
            ? null
            : value - Number(item.values[index - 1]),
      })),
    })),
  };
}

async function renderChart() {
  await nextTick();
  await renderEcharts(buildChartOptions(trendSeries.value));
}

async function loadDetail(skuId: number) {
  loading.value = true;
  try {
    const result = await getInventoryDetailList(skuId);
    inventoryRows.value = result.list || [];
    const first = inventoryRows.value[0] || detailData.value;
    detailData.value = { ...detailData.value, ...first };
    trendSeries.value = await Promise.all(
      inventoryRows.value.map(async (row) => {
        const history = await getInventoryHistoryTrend({
          skuId,
          warehouseId: row.warehouseId,
        });
        const points = (history.list || [])
          .slice()
          .sort((a, b) => getTimestamp(a.createTime) - getTimestamp(b.createTime))
          .map((item) => ({
            beforeQuantity: Number(item.beforeQuantity ?? 0),
            time: formatDateTime(item.createTime) || '-',
            quantity: Number(item.afterQuantity ?? 0),
          }));
        if (points.length === 0 || points[points.length - 1]?.quantity !== Number(row.quantity || 0)) {
          points.push({
            beforeQuantity: Number(row.quantity || 0),
            time: formatDateTime(new Date()) || '-',
            quantity: Number(row.quantity || 0),
          });
        }
        return {
          warehouseId: row.warehouseId,
          warehouseName: row.warehouseName || `仓库 ${row.warehouseId || '-'}`,
          points,
        };
      }),
    );
    await renderChart();
  } finally {
    loading.value = false;
  }
}

function startRefresh(skuId: number) {
  stopRefresh();
  refreshTimer = setInterval(() => void loadDetail(skuId), 30_000);
}

function stopRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = undefined;
  }
}

async function handleWarehouseChange(value: number | string) {
  activeWarehouseId.value = Number(value) || undefined;
  await renderChart();
}

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen: boolean) {
    if (!isOpen) {
      stopRefresh();
      detailData.value = {};
      inventoryRows.value = [];
      trendSeries.value = [];
      activeWarehouseId.value = undefined;
      return;
    }
    const data = modalApi.getData<{ row?: WmsInventoryApi.Inventory }>();
    const row = data?.row;
    if (!row?.skuId) return;
    detailData.value = row;
    modalApi.lock();
    try {
      await loadDetail(row.skuId);
      startRefresh(row.skuId);
    } finally {
      modalApi.unlock();
    }
  },
});

onBeforeUnmount(stopRefresh);
</script>

<template>
  <Modal title="库存详情" class="w-[90vw] max-w-[1200px]" :show-cancel-button="false" :show-confirm-button="false">
    <Spin :spinning="loading">
      <div class="mb-4 grid grid-cols-4 gap-3 rounded-md bg-muted/40 p-4 text-sm">
        <div><span class="text-muted-foreground">商品名称：</span>{{ detailData.itemName || '-' }}</div>
        <div><span class="text-muted-foreground">商品编号：</span>{{ detailData.itemCode || '-' }}</div>
        <div><span class="text-muted-foreground">规格：</span>{{ detailData.skuName || '-' }}</div>
        <div><span class="text-muted-foreground">计量单位：</span>{{ detailData.unit || '-' }}</div>
        <div class="col-span-4 text-base font-semibold">
          当前总库存：{{ formatQuantity(currentQuantity) }} {{ detailData.unit || '' }}
        </div>
      </div>

      <Card title="仓库库存" :bordered="false" class="mb-3">
        <Table :columns="tableColumns" :data-source="inventoryRows" :pagination="false" row-key="id" size="small">
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'quantity'">{{ formatQuantity(record.quantity) }}</template>
            <template v-else-if="column.dataIndex === 'createTime'">{{ formatDateTime(record.createTime) || '-' }}</template>
          </template>
        </Table>
      </Card>

      <Card title="库存趋势" :bordered="false">
        <div class="mb-2 flex justify-end">
          <Segmented :options="warehouseOptions" :value="activeWarehouseId || 0" @change="handleWarehouseChange" />
        </div>
        <div class="mb-2 text-sm">
          <span class="font-medium">{{ trendConclusion }}</span>
          <span class="ml-2 text-muted-foreground">库存下降也可能受调拨、盘点影响，畅销判断建议结合出库量。</span>
        </div>
        <EchartsUI ref="chartRef" height="320px" />
      </Card>
    </Spin>
  </Modal>
</template>
