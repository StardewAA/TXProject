import type { EChartsOption } from '@vben/plugins/echarts';

import type { WmsHomeStatisticsApi } from '#/api/wms/home';

import { formatQuantity } from '#/views/wms/utils/format';

export interface InventoryChartItem {
  name: string;
  value: number;
}

/** 格式化库存数量展示文本 */
export function formatQuantityText(value?: number) {
  return formatQuantity(value || 0) || '0.00';
}

/** 转换库存排行接口数据为 ECharts 可消费的 name/value 数据 */
export function buildInventoryChartItemList(
  list: undefined | WmsHomeStatisticsApi.InventoryRankItem[],
  emptyName: string,
) {
  return (list || [])
    .map((item) => ({
      name: item.name || emptyName,
      value: Number(item.quantity || 0),
    }))
    .filter((item) => item.value > 0);
}

/** 货物占比配色（图例在组件里用 HTML 渲染，需与此保持一致） */
export const GOODS_SHARE_COLORS = [
  '#2f7df6',
  '#18a058',
  '#f59e0b',
  '#7c3aed',
  '#14b8a6',
];

/**
 * 图例不使用 ECharts 内置 legend，改由组件用 HTML 渲染。
 *
 * 原因：首页这张卡片在 AI 首页里只占约 3/10 宽（约 360px），
 * 而 ECharts 图例不会自动换行与截断文本，只限制图例盒宽度挡不住文字溢出，
 * 文字会一直向左压到圆环上。改用 HTML + CSS（truncate）后，
 * 截断、换行、与圆环的间距都由布局控制，不会再有重叠。
 */
export function getGoodsShareChartOptions(
  goodsShareList: InventoryChartItem[],
): EChartsOption {
  return {
    color: GOODS_SHARE_COLORS,
    legend: { show: false },
    series: [
      {
        avoidLabelOverlap: true,
        center: ['50%', '52%'],
        data: goodsShareList,
        label: { show: false },
        labelLine: { show: false },
        name: '货物占比',
        radius: ['46%', '68%'],
        type: 'pie',
      },
    ],
    tooltip: {
      formatter: '{b}<br/>库存：{c} ({d}%)',
      trigger: 'item',
    },
  };
}

/** 库存分布图表配置 */
export function getWarehouseDistributionChartOptions(
  warehouseDistributionList: InventoryChartItem[],
): EChartsOption {
  const sortedList = warehouseDistributionList.toReversed();
  return {
    color: ['#2f7df6'],
    grid: { bottom: 16, containLabel: true, left: 24, right: 40, top: 12 },
    series: [
      {
        barMaxWidth: 16,
        data: sortedList.map((item) => item.value),
        label: {
          formatter: ({ value }) => formatQuantityText(Number(value || 0)),
          position: 'right',
          show: true,
        },
        name: '库存',
        type: 'bar',
      },
    ],
    tooltip: {
      formatter: (params: unknown) => {
        const item = (Array.isArray(params) ? params[0] : params) as {
          name?: string;
          value?: number;
        };
        return `${item.name || '-'}<br/>库存：${formatQuantityText(item.value)}`;
      },
      trigger: 'axis',
    },
    xAxis: {
      splitLine: { lineStyle: { color: '#eef2f7' } },
      type: 'value',
    },
    yAxis: {
      axisLine: { show: false },
      axisTick: { show: false },
      data: sortedList.map((item) => item.name),
      type: 'category',
    },
  };
}
