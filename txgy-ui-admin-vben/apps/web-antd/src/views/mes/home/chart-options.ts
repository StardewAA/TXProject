import type { EChartsOption } from '@vben/plugins/echarts';

/** 生产趋势折线图配置 */
export function getProductionTrendChartOptions(
  dates: string[],
  quantities: number[],
  qualified: number[],
  unqualified: number[],
): EChartsOption {
  return {
    grid: { bottom: 40, left: 50, right: 20, top: 20 },
    legend: { bottom: 0, data: ['产量', '合格品', '不良品'] },
    series: [
      {
        areaStyle: { color: 'rgba(64,158,255,0.15)' },
        data: quantities,
        itemStyle: { color: '#409EFF' },
        name: '产量',
        smooth: true,
        type: 'line',
      },
      {
        data: qualified,
        itemStyle: { color: '#67C23A' },
        name: '合格品',
        smooth: true,
        type: 'line',
      },
      {
        data: unqualified,
        itemStyle: { color: '#F56C6C' },
        name: '不良品',
        smooth: true,
        type: 'line',
      },
    ],
    tooltip: { axisPointer: { type: 'cross' }, trigger: 'axis' },
    xAxis: { boundaryGap: false, data: dates, type: 'category' },
    yAxis: { minInterval: 1, type: 'value' },
  };
}

/** 工单状态分布饼图配置 */
export function getWorkOrderStatusChartOptions(
  data: Array<{ itemStyle: { color: string }; name: string; value: number }>,
): EChartsOption {
  return {
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        avoidLabelOverlap: true,
        data,
        emphasis: { label: { fontSize: 14, fontWeight: 'bold', show: true } },
        itemStyle: { borderColor: '#fff', borderRadius: 6, borderWidth: 2 },
        label: { formatter: '{b}\n{c}', show: true },
        radius: ['40%', '70%'],
        type: 'pie',
      },
    ],
    tooltip: { formatter: '{b}: {c} ({d}%)', trigger: 'item' },
  };
}

/** 设备状态分布饼图配置 */
export function getMachineryStatusChartOptions(
  data: Array<{ itemStyle: { color: string }; name: string; value: number }>,
): EChartsOption {
  return {
    legend: { bottom: 0, type: 'scroll' },
    series: [
      {
        avoidLabelOverlap: true,
        data,
        emphasis: { label: { fontSize: 14, fontWeight: 'bold', show: true } },
        itemStyle: { borderColor: '#fff', borderRadius: 6, borderWidth: 2 },
        label: { formatter: '{b}\n{c}', show: true },
        radius: ['40%', '70%'],
        type: 'pie',
      },
    ],
    tooltip: { formatter: '{b}: {c} ({d}%)', trigger: 'item' },
  };
}

/** 质检合格率柱状图配置 */
export function getQcPassRateChartOptions(
  categories: string[],
  quantityRates: number[],
  docRates: number[],
): EChartsOption {
  return {
    grid: { bottom: 40, left: 50, right: 20, top: 30 },
    legend: { bottom: 0, data: ['数量合格率', '单据合格率'] },
    series: [
      {
        barWidth: 16,
        data: quantityRates,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#67C23A' },
        name: '数量合格率',
        type: 'bar',
      },
      {
        barWidth: 16,
        data: docRates,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#409EFF' },
        name: '单据合格率',
        type: 'bar',
      },
    ],
    tooltip: { formatter: '{b}<br/>{a}: {c}%', trigger: 'axis' },
    xAxis: { data: categories, type: 'category' },
    yAxis: { max: 100, min: 0, name: '%', type: 'value' },
  };
}

/** 不良缺陷 TOP N 横向柱状图配置 */
export function getDefectTopChartOptions(
  categories: string[],
  quantities: number[],
): EChartsOption {
  return {
    grid: { bottom: 20, left: 90, right: 50, top: 20 },
    series: [
      {
        barWidth: 18,
        data: quantities,
        itemStyle: { borderRadius: [0, 4, 4, 0], color: '#F56C6C' },
        label: { position: 'right', show: true },
        name: '缺陷数量',
        type: 'bar',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value' },
    yAxis: { data: categories, inverse: true, type: 'category' },
  };
}

/** 点检保养执行柱状图配置（总数 vs 已完成） */
export function getCheckMaintenChartOptions(
  categories: string[],
  totalCounts: number[],
  finishedCounts: number[],
): EChartsOption {
  return {
    grid: { bottom: 40, left: 50, right: 20, top: 30 },
    legend: { bottom: 0, data: ['总数', '已完成'] },
    series: [
      {
        barWidth: 24,
        data: totalCounts,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#909399' },
        name: '总数',
        type: 'bar',
      },
      {
        barWidth: 24,
        data: finishedCounts,
        itemStyle: { borderRadius: [4, 4, 0, 0], color: '#67C23A' },
        name: '已完成',
        type: 'bar',
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: { data: categories, type: 'category' },
    yAxis: { minInterval: 1, type: 'value' },
  };
}

/** 安灯呼叫原因 TOP N 横向柱状图配置 */
export function getAndonReasonChartOptions(
  reasons: string[],
  counts: number[],
): EChartsOption {
  return {
    grid: { bottom: 10, left: 100, right: 40, top: 10 },
    series: [
      {
        barWidth: 14,
        data: counts,
        itemStyle: { borderRadius: [0, 4, 4, 0], color: '#E6A23C' },
        label: { position: 'right', show: true },
        type: 'bar',
      },
    ],
    tooltip: { formatter: '{b}: {c} 次', trigger: 'axis' },
    xAxis: { minInterval: 1, type: 'value' },
    yAxis: { data: reasons, inverse: true, type: 'category' },
  };
}
